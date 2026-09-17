package com.autobank.deposit.service;

import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.autobank.deposit.model.DepositInstallment;
import com.autobank.deposit.model.TermDeposit;
import com.autobank.util.AuditLogger;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DepositService {

    public TermDeposit openDeposit(int accountId, String depositType, BigDecimal principalAmount,
                                  BigDecimal monthlyInstallment, int tenureMonths, BigDecimal interestRate,
                                  boolean deductFromSavings) throws SQLException {
        int opId = UserSession.getInstance().getCurrentUser().getId();
        LocalDate today = LocalDate.now();
        LocalDate maturityDate = today.plusMonths(tenureMonths);

        BigDecimal maturityAmount;
        BigDecimal initialDeposit;
        if ("FD".equalsIgnoreCase(depositType)) {
            maturityAmount = DepositCalculator.calculateFdMaturity(principalAmount, interestRate, tenureMonths);
            initialDeposit = principalAmount;
            monthlyInstallment = BigDecimal.ZERO;
        } else {
            maturityAmount = DepositCalculator.calculateRdMaturity(monthlyInstallment, interestRate, tenureMonths);
            initialDeposit = monthlyInstallment;
            principalAmount = monthlyInstallment.multiply(BigDecimal.valueOf(tenureMonths));
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Check account
                PreparedStatement acctPs = conn.prepareStatement("SELECT account_number, holder_name, balance FROM accounts WHERE id = ? FOR UPDATE");
                acctPs.setInt(1, accountId);
                ResultSet acctRs = acctPs.executeQuery();
                if (!acctRs.next()) {
                    throw new SQLException("Account #" + accountId + " not found");
                }
                String acctNo = acctRs.getString("account_number");
                String holderName = acctRs.getString("holder_name");
                BigDecimal balance = acctRs.getBigDecimal("balance");

                Integer linkedTxId = null;
                if (deductFromSavings) {
                    if (balance.compareTo(initialDeposit) < 0) {
                        throw new SQLException("Insufficient savings balance (Available: ₹" + balance + ", Required: ₹" + initialDeposit + ")");
                    }
                    BigDecimal newBalance = balance.subtract(initialDeposit);
                    PreparedStatement updAcct = conn.prepareStatement("UPDATE accounts SET balance = ? WHERE id = ?");
                    updAcct.setBigDecimal(1, newBalance);
                    updAcct.setInt(2, accountId);
                    updAcct.executeUpdate();

                    PreparedStatement txPs = conn.prepareStatement(
                        "INSERT INTO transactions (account_id, type, amount, balance_before, balance_after, description, operator_id) " +
                        "VALUES (?, 'WITHDRAWAL', ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                    txPs.setInt(1, accountId);
                    txPs.setBigDecimal(2, initialDeposit);
                    txPs.setBigDecimal(3, balance);
                    txPs.setBigDecimal(4, newBalance);
                    txPs.setString(5, depositType.toUpperCase() + " Opening Deposit");
                    txPs.setInt(6, opId);
                    txPs.executeUpdate();
                    ResultSet txRs = txPs.getGeneratedKeys();
                    if (txRs.next()) linkedTxId = txRs.getInt(1);
                }

                // 2. Generate unique deposit number
                String depNumber = generateDepositNumber(conn, depositType);

                // 3. Insert term_deposits
                PreparedStatement depPs = conn.prepareStatement(
                    "INSERT INTO term_deposits (deposit_number, account_id, deposit_type, principal_amount, monthly_installment, " +
                    "tenure_months, interest_rate, maturity_amount, deposit_date, maturity_date, status, total_deposited, operator_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)", Statement.RETURN_GENERATED_KEYS);
                depPs.setString(1, depNumber);
                depPs.setInt(2, accountId);
                depPs.setString(3, depositType.toUpperCase());
                depPs.setBigDecimal(4, principalAmount);
                depPs.setBigDecimal(5, monthlyInstallment);
                depPs.setInt(6, tenureMonths);
                depPs.setBigDecimal(7, interestRate);
                depPs.setBigDecimal(8, maturityAmount);
                depPs.setDate(9, Date.valueOf(today));
                depPs.setDate(10, Date.valueOf(maturityDate));
                depPs.setBigDecimal(11, initialDeposit);
                depPs.setInt(12, opId);
                depPs.executeUpdate();

                ResultSet depRs = depPs.getGeneratedKeys();
                int depositId = 0;
                if (depRs.next()) depositId = depRs.getInt(1);

                // 4. If RD, insert 1st installment
                if ("RD".equalsIgnoreCase(depositType)) {
                    PreparedStatement instPs = conn.prepareStatement(
                        "INSERT INTO term_deposit_installments (deposit_id, installment_number, amount, payment_date, transaction_id, operator_id) " +
                        "VALUES (?, 1, ?, ?, ?, ?)");
                    instPs.setInt(1, depositId);
                    instPs.setBigDecimal(2, initialDeposit);
                    instPs.setDate(3, Date.valueOf(today));
                    if (linkedTxId != null) instPs.setInt(4, linkedTxId); else instPs.setNull(4, Types.INTEGER);
                    instPs.setInt(5, opId);
                    instPs.executeUpdate();
                }

                conn.commit();

                AuditLogger.log("DEPOSIT_OPENED", "DEPOSIT", depositId,
                    depositType + " " + depNumber + " opened for Account " + acctNo + " (₹" + initialDeposit + ", " + tenureMonths + "M @ " + interestRate + "%)", opId);

                TermDeposit td = new TermDeposit();
                td.setId(depositId);
                td.setDepositNumber(depNumber);
                td.setAccountId(accountId);
                td.setAccountNumber(acctNo);
                td.setHolderName(holderName);
                td.setDepositType(depositType.toUpperCase());
                td.setPrincipalAmount(principalAmount);
                td.setMonthlyInstallment(monthlyInstallment);
                td.setTenureMonths(tenureMonths);
                td.setInterestRate(interestRate);
                td.setMaturityAmount(maturityAmount);
                td.setDepositDate(today);
                td.setMaturityDate(maturityDate);
                td.setStatus("ACTIVE");
                td.setTotalDeposited(initialDeposit);
                return td;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void payRdInstallment(int depositId, boolean deductFromSavings) throws SQLException {
        int opId = UserSession.getInstance().getCurrentUser().getId();
        LocalDate today = LocalDate.now();

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Fetch deposit
                PreparedStatement selPs = conn.prepareStatement(
                    "SELECT d.*, a.account_number, a.balance FROM term_deposits d " +
                    "JOIN accounts a ON d.account_id = a.id WHERE d.id = ? AND d.status = 'ACTIVE' AND d.deposit_type = 'RD' FOR UPDATE");
                selPs.setInt(1, depositId);
                ResultSet rs = selPs.executeQuery();
                if (!rs.next()) {
                    throw new SQLException("Active Recurring Deposit not found");
                }

                int accountId = rs.getInt("account_id");
                String acctNo = rs.getString("account_number");
                BigDecimal installmentAmount = rs.getBigDecimal("monthly_installment");
                BigDecimal currentBalance = rs.getBigDecimal("balance");
                BigDecimal currentDeposited = rs.getBigDecimal("total_deposited");
                int tenureMonths = rs.getInt("tenure_months");

                // Check installment count
                PreparedStatement countPs = conn.prepareStatement(
                    "SELECT COUNT(*) FROM term_deposit_installments WHERE deposit_id = ?");
                countPs.setInt(1, depositId);
                ResultSet countRs = countPs.executeQuery();
                int nextInstallmentNo = 1;
                if (countRs.next()) {
                    nextInstallmentNo = countRs.getInt(1) + 1;
                }

                if (nextInstallmentNo > tenureMonths) {
                    throw new SQLException("All " + tenureMonths + " installments have already been paid for this RD.");
                }

                Integer linkedTxId = null;
                if (deductFromSavings) {
                    if (currentBalance.compareTo(installmentAmount) < 0) {
                        throw new SQLException("Insufficient savings balance (Available: ₹" + currentBalance + ", Required: ₹" + installmentAmount + ")");
                    }
                    BigDecimal newBal = currentBalance.subtract(installmentAmount);
                    PreparedStatement updAcct = conn.prepareStatement("UPDATE accounts SET balance = ? WHERE id = ?");
                    updAcct.setBigDecimal(1, newBal);
                    updAcct.setInt(2, accountId);
                    updAcct.executeUpdate();

                    PreparedStatement txPs = conn.prepareStatement(
                        "INSERT INTO transactions (account_id, type, amount, balance_before, balance_after, description, operator_id) " +
                        "VALUES (?, 'WITHDRAWAL', ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                    txPs.setInt(1, accountId);
                    txPs.setBigDecimal(2, installmentAmount);
                    txPs.setBigDecimal(3, currentBalance);
                    txPs.setBigDecimal(4, newBal);
                    txPs.setString(5, "RD Installment #" + nextInstallmentNo + " for Deposit #" + depositId);
                    txPs.setInt(6, opId);
                    txPs.executeUpdate();
                    ResultSet txRs = txPs.getGeneratedKeys();
                    if (txRs.next()) linkedTxId = txRs.getInt(1);
                }

                // Insert installment
                PreparedStatement instPs = conn.prepareStatement(
                    "INSERT INTO term_deposit_installments (deposit_id, installment_number, amount, payment_date, transaction_id, operator_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?)");
                instPs.setInt(1, depositId);
                instPs.setInt(2, nextInstallmentNo);
                instPs.setBigDecimal(3, installmentAmount);
                instPs.setDate(4, Date.valueOf(today));
                if (linkedTxId != null) instPs.setInt(5, linkedTxId); else instPs.setNull(5, Types.INTEGER);
                instPs.setInt(6, opId);
                instPs.executeUpdate();

                // Update total deposited
                BigDecimal newTotalDeposited = currentDeposited.add(installmentAmount);
                PreparedStatement updDep = conn.prepareStatement(
                    "UPDATE term_deposits SET total_deposited = ? WHERE id = ?");
                updDep.setBigDecimal(1, newTotalDeposited);
                updDep.setInt(2, depositId);
                updDep.executeUpdate();

                conn.commit();

                AuditLogger.log("RD_INSTALLMENT_PAID", "DEPOSIT", depositId,
                    "RD Installment #" + nextInstallmentNo + " of ₹" + installmentAmount + " recorded for Account " + acctNo, opId);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void settleDeposit(int depositId, boolean isPremature, BigDecimal penaltyRate, boolean creditToSavings) throws SQLException {
        int opId = UserSession.getInstance().getCurrentUser().getId();
        LocalDate today = LocalDate.now();

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                PreparedStatement selPs = conn.prepareStatement(
                    "SELECT d.*, a.account_number, a.balance FROM term_deposits d " +
                    "JOIN accounts a ON d.account_id = a.id WHERE d.id = ? AND d.status = 'ACTIVE' FOR UPDATE");
                selPs.setInt(1, depositId);
                ResultSet rs = selPs.executeQuery();
                if (!rs.next()) {
                    throw new SQLException("Active deposit not found or already closed");
                }

                TermDeposit deposit = map(rs);
                int accountId = deposit.getAccountId();
                String acctNo = rs.getString("account_number");
                BigDecimal currentAcctBal = rs.getBigDecimal("balance");

                BigDecimal payoutAmount;
                BigDecimal interestPaid;
                String finalStatus;

                if (isPremature) {
                    DepositCalculator.PrematureSettlement settlement = DepositCalculator.calculatePrematurePayout(deposit, today, penaltyRate);
                    payoutAmount = settlement.getTotalPayout();
                    interestPaid = settlement.getInterest();
                    finalStatus = "PREMATURE_CLOSED";
                } else {
                    payoutAmount = deposit.getMaturityAmount();
                    interestPaid = payoutAmount.subtract(deposit.getTotalDeposited()).max(BigDecimal.ZERO);
                    finalStatus = "CLOSED";
                }

                if (creditToSavings) {
                    BigDecimal newBal = currentAcctBal.add(payoutAmount);
                    PreparedStatement updAcct = conn.prepareStatement("UPDATE accounts SET balance = ? WHERE id = ?");
                    updAcct.setBigDecimal(1, newBal);
                    updAcct.setInt(2, accountId);
                    updAcct.executeUpdate();

                    PreparedStatement txPs = conn.prepareStatement(
                        "INSERT INTO transactions (account_id, type, amount, balance_before, balance_after, description, operator_id) " +
                        "VALUES (?, 'DEPOSIT', ?, ?, ?, ?, ?)");
                    txPs.setInt(1, accountId);
                    txPs.setBigDecimal(2, payoutAmount);
                    txPs.setBigDecimal(3, currentAcctBal);
                    txPs.setBigDecimal(4, newBal);
                    txPs.setString(5, deposit.getDepositType() + " Settlement Payout (" + deposit.getDepositNumber() + ")");
                    txPs.setInt(6, opId);
                    txPs.executeUpdate();
                }

                // Update deposit record
                PreparedStatement updDep = conn.prepareStatement(
                    "UPDATE term_deposits SET status = ?, payout_amount = ?, interest_paid = ?, closed_at = CURRENT_TIMESTAMP WHERE id = ?");
                updDep.setString(1, finalStatus);
                updDep.setBigDecimal(2, payoutAmount);
                updDep.setBigDecimal(3, interestPaid);
                updDep.setInt(4, depositId);
                updDep.executeUpdate();

                conn.commit();

                AuditLogger.log("DEPOSIT_SETTLED", "DEPOSIT", depositId,
                    deposit.getDepositType() + " " + deposit.getDepositNumber() + " settled. Payout: ₹" + payoutAmount + " (Status: " + finalStatus + ") to Account " + acctNo, opId);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public List<TermDeposit> getAllDeposits(String statusFilter, String typeFilter) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT d.*, a.account_number, a.holder_name FROM term_deposits d " +
            "JOIN accounts a ON d.account_id = a.id WHERE 1=1 ");

        if (!"ALL".equalsIgnoreCase(statusFilter) && statusFilter != null && !statusFilter.isBlank()) {
            if ("DUE_SOON".equalsIgnoreCase(statusFilter)) {
                sql.append("AND d.status = 'ACTIVE' AND d.maturity_date <= CURRENT_DATE + INTERVAL 30 DAY ");
            } else {
                sql.append("AND d.status = ? ");
            }
        }
        if (!"ALL".equalsIgnoreCase(typeFilter) && typeFilter != null && !typeFilter.isBlank()) {
            sql.append("AND d.deposit_type = ? ");
        }
        sql.append("ORDER BY d.created_at DESC");

        List<TermDeposit> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int p = 1;
            if (!"ALL".equalsIgnoreCase(statusFilter) && statusFilter != null && !statusFilter.isBlank() && !"DUE_SOON".equalsIgnoreCase(statusFilter)) {
                ps.setString(p++, statusFilter);
            }
            if (!"ALL".equalsIgnoreCase(typeFilter) && typeFilter != null && !typeFilter.isBlank()) {
                ps.setString(p++, typeFilter);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<TermDeposit> searchDeposits(String query) throws SQLException {
        String sql = "SELECT d.*, a.account_number, a.holder_name FROM term_deposits d " +
                     "JOIN accounts a ON d.account_id = a.id " +
                     "WHERE d.deposit_number LIKE ? OR a.account_number LIKE ? OR a.holder_name LIKE ? " +
                     "ORDER BY d.created_at DESC";
        String q = "%" + query.trim() + "%";
        List<TermDeposit> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q);
            ps.setString(2, q);
            ps.setString(3, q);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<DepositInstallment> getInstallmentsForDeposit(int depositId) throws SQLException {
        String sql = "SELECT * FROM term_deposit_installments WHERE deposit_id = ? ORDER BY installment_number ASC";
        List<DepositInstallment> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, depositId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                DepositInstallment inst = new DepositInstallment();
                inst.setId(rs.getInt("id"));
                inst.setDepositId(rs.getInt("deposit_id"));
                inst.setInstallmentNumber(rs.getInt("installment_number"));
                inst.setAmount(rs.getBigDecimal("amount"));
                Date pd = rs.getDate("payment_date");
                if (pd != null) inst.setPaymentDate(pd.toLocalDate());
                int txId = rs.getInt("transaction_id");
                if (!rs.wasNull()) inst.setTransactionId(txId);
                inst.setOperatorId(rs.getInt("operator_id"));
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) inst.setCreatedAt(ts.toLocalDateTime());
                list.add(inst);
            }
        }
        return list;
    }

    public DepositStats getDepositStats() throws SQLException {
        String sql = "SELECT " +
                     "COALESCE(SUM(CASE WHEN deposit_type = 'FD' AND status = 'ACTIVE' THEN total_deposited ELSE 0 END), 0) as fd_volume, " +
                     "COALESCE(SUM(CASE WHEN deposit_type = 'RD' AND status = 'ACTIVE' THEN total_deposited ELSE 0 END), 0) as rd_volume, " +
                     "COUNT(CASE WHEN status = 'ACTIVE' AND maturity_date <= CURRENT_DATE + INTERVAL 30 DAY THEN 1 END) as due_soon_count, " +
                     "COUNT(CASE WHEN status = 'ACTIVE' THEN 1 END) as active_count " +
                     "FROM term_deposits";

        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return new DepositStats(
                    rs.getBigDecimal("fd_volume"),
                    rs.getBigDecimal("rd_volume"),
                    rs.getInt("due_soon_count"),
                    rs.getInt("active_count")
                );
            }
        }
        return new DepositStats(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
    }

    private String generateDepositNumber(Connection conn, String type) throws SQLException {
        int year = LocalDate.now().getYear();
        String prefix = type.toUpperCase() + "-" + year + "-";
        String sql = "SELECT COUNT(*) FROM term_deposits WHERE deposit_number LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            ResultSet rs = ps.executeQuery();
            int count = 0;
            if (rs.next()) count = rs.getInt(1);
            return String.format("%s%04d", prefix, count + 1);
        }
    }

    private TermDeposit map(ResultSet rs) throws SQLException {
        TermDeposit td = new TermDeposit();
        td.setId(rs.getInt("id"));
        td.setDepositNumber(rs.getString("deposit_number"));
        td.setAccountId(rs.getInt("account_id"));
        td.setAccountNumber(rs.getString("account_number"));
        td.setHolderName(rs.getString("holder_name"));
        td.setDepositType(rs.getString("deposit_type"));
        td.setPrincipalAmount(rs.getBigDecimal("principal_amount"));
        td.setMonthlyInstallment(rs.getBigDecimal("monthly_installment"));
        td.setTenureMonths(rs.getInt("tenure_months"));
        td.setInterestRate(rs.getBigDecimal("interest_rate"));
        td.setMaturityAmount(rs.getBigDecimal("maturity_amount"));
        Date dd = rs.getDate("deposit_date");
        if (dd != null) td.setDepositDate(dd.toLocalDate());
        Date md = rs.getDate("maturity_date");
        if (md != null) td.setMaturityDate(md.toLocalDate());
        td.setStatus(rs.getString("status"));
        td.setTotalDeposited(rs.getBigDecimal("total_deposited"));
        td.setInterestPaid(rs.getBigDecimal("interest_paid"));
        td.setPayoutAmount(rs.getBigDecimal("payout_amount"));
        Timestamp ca = rs.getTimestamp("closed_at");
        if (ca != null) td.setClosedAt(ca.toLocalDateTime());
        td.setOperatorId(rs.getInt("operator_id"));
        Timestamp cr = rs.getTimestamp("created_at");
        if (cr != null) td.setCreatedAt(cr.toLocalDateTime());
        return td;
    }

    public static class DepositStats {
        private final BigDecimal fdVolume;
        private final BigDecimal rdVolume;
        private final int dueSoonCount;
        private final int activeCount;

        public DepositStats(BigDecimal fdVolume, BigDecimal rdVolume, int dueSoonCount, int activeCount) {
            this.fdVolume = fdVolume;
            this.rdVolume = rdVolume;
            this.dueSoonCount = dueSoonCount;
            this.activeCount = activeCount;
        }

        public BigDecimal getFdVolume() { return fdVolume; }
        public BigDecimal getRdVolume() { return rdVolume; }
        public int getDueSoonCount() { return dueSoonCount; }
        public int getActiveCount() { return activeCount; }
    }
}
