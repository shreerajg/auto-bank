package com.autobank.loan.service;

import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.autobank.loan.model.Loan;
import com.autobank.util.AuditLogger;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LoanService {

    public Loan disburseLoan(int accountId, BigDecimal amount, BigDecimal interestRate,
                              BigDecimal installment, String dueDateStr) throws SQLException {
        int opId = UserSession.getInstance().getCurrentUser().getId();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO loans (account_id, amount, interest_rate, installment_amount, outstanding, due_date, operator_id) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, accountId);
            ps.setBigDecimal(2, amount);
            ps.setBigDecimal(3, interestRate);
            ps.setBigDecimal(4, installment);
            ps.setBigDecimal(5, amount);
            ps.setString(6, dueDateStr);
            ps.setInt(7, opId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            Loan loan = new Loan();
            if (rs.next()) {
                loan.setId(rs.getInt(1));
                AuditLogger.log("LOAN_DISBURSED", "LOAN", loan.getId(),
                        "Loan ₹" + amount + " disbursed to account #" + accountId, opId);
            }
            loan.setAccountId(accountId);
            loan.setAmount(amount);
            loan.setInterestRate(interestRate);
            loan.setInstallmentAmount(installment);
            loan.setOutstanding(amount);
            loan.setTotalPaid(BigDecimal.ZERO);
            loan.setStatus("ACTIVE");
            return loan;
        }
    }

    public void recordPayment(int loanId, BigDecimal payment) throws SQLException {
        int opId = UserSession.getInstance().getCurrentUser().getId();
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                PreparedStatement sel = conn.prepareStatement(
                        "SELECT outstanding, total_paid FROM loans WHERE id = ? AND status = 'ACTIVE' FOR UPDATE");
                sel.setInt(1, loanId);
                ResultSet rs = sel.executeQuery();
                if (!rs.next()) throw new SQLException("Loan not found or already closed");
                BigDecimal outstanding = rs.getBigDecimal("outstanding");
                BigDecimal totalPaid   = rs.getBigDecimal("total_paid");
                BigDecimal newOutstanding = outstanding.subtract(payment);
                BigDecimal newPaid = totalPaid.add(payment);
                String newStatus = newOutstanding.compareTo(BigDecimal.ZERO) <= 0 ? "CLOSED" : "ACTIVE";

                PreparedStatement upd = conn.prepareStatement(
                        "UPDATE loans SET outstanding = ?, total_paid = ?, status = ? WHERE id = ?");
                upd.setBigDecimal(1, newOutstanding.max(BigDecimal.ZERO));
                upd.setBigDecimal(2, newPaid);
                upd.setString(3, newStatus);
                upd.setInt(4, loanId);
                upd.executeUpdate();

                PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO loan_payments (loan_id, amount, operator_id) VALUES (?, ?, ?)");
                ins.setInt(1, loanId);
                ins.setBigDecimal(2, payment);
                ins.setInt(3, opId);
                ins.executeUpdate();

                conn.commit();
                AuditLogger.log("LOAN_PAYMENT", "LOAN", loanId,
                        "Payment ₹" + payment + " recorded", opId);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public List<Loan> getAllLoans(String statusFilter) throws SQLException {
        String sql = "SELECT l.*, a.account_number, a.holder_name FROM loans l " +
                     "JOIN accounts a ON l.account_id = a.id ";
        if (!"ALL".equals(statusFilter)) sql += "WHERE l.status = ? ";
        sql += "ORDER BY l.disbursed_at DESC";

        List<Loan> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!"ALL".equals(statusFilter)) ps.setString(1, statusFilter);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private Loan map(ResultSet rs) throws SQLException {
        Loan l = new Loan();
        l.setId(rs.getInt("id"));
        l.setAccountId(rs.getInt("account_id"));
        l.setAccountNumber(rs.getString("account_number"));
        l.setHolderName(rs.getString("holder_name"));
        l.setAmount(rs.getBigDecimal("amount"));
        l.setInterestRate(rs.getBigDecimal("interest_rate"));
        l.setInstallmentAmount(rs.getBigDecimal("installment_amount"));
        l.setTotalPaid(rs.getBigDecimal("total_paid"));
        l.setOutstanding(rs.getBigDecimal("outstanding"));
        l.setStatus(rs.getString("status"));
        Timestamp ts = rs.getTimestamp("disbursed_at");
        if (ts != null) l.setDisbursedAt(ts.toLocalDateTime());
        Date dd = rs.getDate("due_date");
        if (dd != null) l.setDueDate(dd.toLocalDate());
        return l;
    }
}
