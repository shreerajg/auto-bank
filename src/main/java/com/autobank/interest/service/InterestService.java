package com.autobank.interest.service;

import com.autobank.account.model.Account;
import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.autobank.loan.model.Loan;
import com.autobank.transaction.model.Transaction;
import com.autobank.transaction.service.TransactionService;
import com.autobank.util.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InterestService {
    private static final Logger log = LoggerFactory.getLogger(InterestService.class);
    private final TransactionService transactionService = new TransactionService();

    public static class InterestPreview {
        public int count;
        public BigDecimal totalAmount;
    }

    public InterestPreview previewSavingsInterest(int year, int month) throws SQLException {
        InterestPreview preview = new InterestPreview();
        preview.totalAmount = BigDecimal.ZERO;
        
        String sql = "SELECT balance, interest_rate FROM accounts WHERE status = 'ACTIVE'";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                BigDecimal balance = rs.getBigDecimal("balance");
                BigDecimal rate = rs.getBigDecimal("interest_rate");
                BigDecimal interest = calculateMonthlyInterest(balance, rate);
                if (interest.compareTo(BigDecimal.ZERO) > 0) {
                    preview.count++;
                    preview.totalAmount = preview.totalAmount.add(interest);
                }
            }
        }
        return preview;
    }

    public InterestPreview previewLoanInterest(int year, int month) throws SQLException {
        InterestPreview preview = new InterestPreview();
        preview.totalAmount = BigDecimal.ZERO;
        
        String sql = "SELECT outstanding, interest_rate FROM loans WHERE status = 'ACTIVE'";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                BigDecimal outstanding = rs.getBigDecimal("outstanding");
                BigDecimal rate = rs.getBigDecimal("interest_rate");
                BigDecimal interest = calculateMonthlyInterest(outstanding, rate);
                if (interest.compareTo(BigDecimal.ZERO) > 0) {
                    preview.count++;
                    preview.totalAmount = preview.totalAmount.add(interest);
                }
            }
        }
        return preview;
    }

    public void processSavingsInterest(int year, int month) throws Exception {
        checkIfBatchExists(year, month, "SAVINGS");
        int opId = UserSession.getInstance().getCurrentUser().getId();

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<Account> accounts = getActiveAccounts(conn);
                BigDecimal totalInterest = BigDecimal.ZERO;
                int count = 0;

                for (Account acc : accounts) {
                    BigDecimal interest = calculateMonthlyInterest(acc.getBalance(), acc.getInterestRate());
                    if (interest.compareTo(BigDecimal.ZERO) > 0) {
                        transactionService.deposit(acc.getId(), interest, "Monthly Interest - " + month + "/" + year, conn);
                        totalInterest = totalInterest.add(interest);
                        count++;
                    }
                }

                saveBatchRecord(conn, year, month, "SAVINGS", totalInterest, count, opId);
                conn.commit();
                AuditLogger.log("INTEREST_BATCH", "SAVINGS", count, "Processed savings interest for " + month + "/" + year, opId);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void processLoanInterest(int year, int month) throws Exception {
        checkIfBatchExists(year, month, "LOAN");
        int opId = UserSession.getInstance().getCurrentUser().getId();

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String sql = "SELECT * FROM loans WHERE status = 'ACTIVE'";
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery();
                
                BigDecimal totalInterest = BigDecimal.ZERO;
                int count = 0;

                while (rs.next()) {
                    int loanId = rs.getInt("id");
                    int accId = rs.getInt("account_id");
                    BigDecimal outstanding = rs.getBigDecimal("outstanding");
                    BigDecimal rate = rs.getBigDecimal("interest_rate");
                    BigDecimal interest = calculateMonthlyInterest(outstanding, rate);

                    if (interest.compareTo(BigDecimal.ZERO) > 0) {
                        // Add interest to outstanding loan balance
                        String updSql = "UPDATE loans SET outstanding = outstanding + ? WHERE id = ?";
                        try (PreparedStatement upd = conn.prepareStatement(updSql)) {
                            upd.setBigDecimal(1, interest);
                            upd.setInt(2, loanId);
                            upd.executeUpdate();
                        }
                        
                        // Record as a specialized debit transaction
                        transactionService.withdraw(accId, interest, "Loan Interest Accrual #" + loanId + " - " + month + "/" + year, conn);
                        
                        totalInterest = totalInterest.add(interest);
                        count++;
                    }
                }

                saveBatchRecord(conn, year, month, "LOAN", totalInterest, count, opId);
                conn.commit();
                AuditLogger.log("INTEREST_BATCH", "LOAN", count, "Processed loan interest accrual for " + month + "/" + year, opId);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private BigDecimal calculateMonthlyInterest(BigDecimal principal, BigDecimal annualRate) {
        if (principal == null || annualRate == null) return BigDecimal.ZERO;
        return principal.multiply(annualRate)
                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                .divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
    }

    private void checkIfBatchExists(int year, int month, String type) throws Exception {
        String sql = "SELECT id FROM interest_batches WHERE year = ? AND month = ? AND batch_type = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            ps.setString(3, type);
            if (ps.executeQuery().next()) {
                throw new Exception("Interest batch already exists for this period and type.");
            }
        }
    }

    private void saveBatchRecord(Connection conn, int year, int month, String type, BigDecimal total, int count, int opId) throws SQLException {
        String sql = "INSERT INTO interest_batches (year, month, batch_type, total_amount, record_count, operator_id) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            ps.setString(3, type);
            ps.setBigDecimal(4, total);
            ps.setInt(5, count);
            ps.setInt(6, opId);
            ps.executeUpdate();
        }
    }

    private List<Account> getActiveAccounts(Connection conn) throws SQLException {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT * FROM accounts WHERE status = 'ACTIVE'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Account a = new Account();
                a.setId(rs.getInt("id"));
                a.setBalance(rs.getBigDecimal("balance"));
                a.setInterestRate(rs.getBigDecimal("interest_rate"));
                list.add(a);
            }
        }
        return list;
    }
}
