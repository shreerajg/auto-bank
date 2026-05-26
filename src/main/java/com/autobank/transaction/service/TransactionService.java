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
        return execute(accountId, "DEPOSIT", amount, desc, null);
    }

    public Transaction deposit(int accountId, BigDecimal amount, String desc, Connection conn) throws Exception {
        return execute(accountId, "DEPOSIT", amount, desc, conn);
    }

    public Transaction withdraw(int accountId, BigDecimal amount, String desc) throws Exception {
        return execute(accountId, "WITHDRAWAL", amount, desc, null);
    }

    public Transaction withdraw(int accountId, BigDecimal amount, String desc, Connection conn) throws Exception {
        return execute(accountId, "WITHDRAWAL", amount, desc, conn);
    }

    private Transaction execute(int accountId, String type, BigDecimal amount, String desc, Connection externalConn) throws Exception {
        Connection conn = (externalConn != null) ? externalConn : DatabaseConfig.getConnection();
        boolean isInternal = (externalConn == null);
        
        if (isInternal) conn.setAutoCommit(false);
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

            if (isInternal) conn.commit();

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
            if (isInternal) conn.rollback();
            throw e;
        } finally {
            if (isInternal) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    public void reverseTransaction(int txId, String reason) throws Exception {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Fetch original TX
                PreparedStatement sel = conn.prepareStatement("SELECT * FROM transactions WHERE id = ? FOR UPDATE");
                sel.setInt(1, txId);
                ResultSet rs = sel.executeQuery();
                if (!rs.next()) throw new Exception("Transaction not found");
                
                if (!"ACTIVE".equals(rs.getString("status"))) 
                    throw new Exception("Transaction is already " + rs.getString("status"));
                
                int accountId = rs.getInt("account_id");
                String type = rs.getString("type");
                BigDecimal amount = rs.getBigDecimal("amount");
                
                // 2. Lock account
                PreparedStatement lockAcc = conn.prepareStatement("SELECT balance FROM accounts WHERE id = ? FOR UPDATE");
                lockAcc.setInt(1, accountId);
                ResultSet accRs = lockAcc.executeQuery();
                if (!accRs.next()) throw new Exception("Account not found");
                
                BigDecimal balanceBefore = accRs.getBigDecimal("balance");
                BigDecimal balanceAfter;
                String reversalType;
                
                // If it was a credit (deposit/interest), reverse means debit (subtract)
                if (type.contains("DEPOSIT") || type.contains("CREDIT") || type.contains("INTEREST_CREDIT")) {
                    balanceAfter = balanceBefore.subtract(amount);
                    reversalType = "REVERSAL_DEBIT";
                    if (balanceAfter.compareTo(BigDecimal.ZERO) < 0)
                        throw new Exception("Cannot reverse: Insufficient balance to subtract ₹" + amount);
                } else {
                    // It was a debit (withdrawal/accrual), reverse means credit (add)
                    balanceAfter = balanceBefore.add(amount);
                    reversalType = "REVERSAL_CREDIT";
                }
                
                // 3. Update Account
                PreparedStatement updAcc = conn.prepareStatement("UPDATE accounts SET balance = ? WHERE id = ?");
                updAcc.setBigDecimal(1, balanceAfter);
                updAcc.setInt(2, accountId);
                updAcc.executeUpdate();
                
                // 4. Create Reversal TX
                int opId = UserSession.getInstance().getCurrentUser().getId();
                PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO transactions (account_id, type, amount, balance_before, balance_after, description, status, operator_id, reference_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)");
                ins.setInt(1, accountId);
                ins.setString(2, reversalType);
                ins.setBigDecimal(3, amount);
                ins.setBigDecimal(4, balanceBefore);
                ins.setBigDecimal(5, balanceAfter);
                ins.setString(6, "REVERSAL OF #" + txId + ": " + (reason == null ? "No reason provided" : reason));
                ins.setInt(7, opId);
                ins.setInt(8, txId);
                ins.executeUpdate();
                
                // 5. Mark original TX as REVERSED
                PreparedStatement updTx = conn.prepareStatement("UPDATE transactions SET status = 'REVERSED' WHERE id = ?");
                updTx.setInt(1, txId);
                updTx.executeUpdate();
                
                conn.commit();
                AuditLogger.log("TX_REVERSED", "TRANSACTION", txId, "Reversed transaction #" + txId + " (Reason: " + reason + ")", opId);
                
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
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
