package com.autobank.transaction.service;

import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.autobank.transaction.model.Transaction;
import com.autobank.util.AuditLogger;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TransactionService {

    public Transaction deposit(int accountId, BigDecimal amount, String desc) throws Exception {
        return execute(accountId, "DEPOSIT", amount, desc);
    }

    public Transaction withdraw(int accountId, BigDecimal amount, String desc) throws Exception {
        return execute(accountId, "WITHDRAWAL", amount, desc);
    }

    private Transaction execute(int accountId, String type, BigDecimal amount, String desc) throws Exception {
        Connection conn = DatabaseConfig.getConnection();
        conn.setAutoCommit(false);
        try {
            PreparedStatement lockStmt = conn.prepareStatement(
                "SELECT balance FROM accounts WHERE id = ? AND status = 'ACTIVE' FOR UPDATE");
            lockStmt.setInt(1, accountId);
            ResultSet rs = lockStmt.executeQuery();
            if (!rs.next()) throw new Exception("Account not found or inactive");

            BigDecimal before = rs.getBigDecimal("balance");
            BigDecimal after = "DEPOSIT".equals(type) ? before.add(amount) : before.subtract(amount);

            if (after.compareTo(BigDecimal.ZERO) < 0)
                throw new Exception("Insufficient balance. Current: ₹" + before);

            PreparedStatement upd = conn.prepareStatement(
                "UPDATE accounts SET balance = ?, updated_at = NOW() WHERE id = ?");
            upd.setBigDecimal(1, after);
            upd.setInt(2, accountId);
            upd.executeUpdate();

            int opId = UserSession.getInstance().getCurrentUser().getId();
            PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO transactions (account_id, type, amount, balance_before, balance_after, " +
                "description, status, operator_id) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)", Statement.RETURN_GENERATED_KEYS);
            ins.setInt(1, accountId);
            ins.setString(2, type);
            ins.setBigDecimal(3, amount);
            ins.setBigDecimal(4, before);
            ins.setBigDecimal(5, after);
            ins.setString(6, desc);
            ins.setInt(7, opId);
            ins.executeUpdate();
            ResultSet idRs = ins.getGeneratedKeys();

            conn.commit();

            Transaction tx = new Transaction();
            if (idRs.next()) tx.setId(idRs.getInt(1));
            tx.setAccountId(accountId);
            tx.setType(type);
            tx.setAmount(amount);
            tx.setBalanceBefore(before);
            tx.setBalanceAfter(after);
            tx.setDescription(desc);
            tx.setStatus("ACTIVE");

            AuditLogger.log("TX_" + type, "TRANSACTION", tx.getId(),
                            type + " ₹" + amount + " on account #" + accountId, opId);
            return tx;

        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    public List<Transaction> getRecent(int limit) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM transactions ORDER BY created_at DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Transaction tx = new Transaction();
                tx.setId(rs.getInt("id"));
                tx.setAccountId(rs.getInt("account_id"));
                tx.setType(rs.getString("type"));
                tx.setAmount(rs.getBigDecimal("amount"));
                tx.setBalanceBefore(rs.getBigDecimal("balance_before"));
                tx.setBalanceAfter(rs.getBigDecimal("balance_after"));
                tx.setDescription(rs.getString("description"));
                tx.setStatus(rs.getString("status"));
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) tx.setCreatedAt(ts.toLocalDateTime());
                list.add(tx);
            }
        }
        return list;
    }
}
