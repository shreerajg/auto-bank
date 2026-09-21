package com.autobank.transaction.service;

import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.autobank.transaction.model.Transaction;
import com.autobank.util.AuditLogger;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

public class TransactionService {

    public static class CashbookSummary {
        public BigDecimal openingBalance = BigDecimal.ZERO;
        public BigDecimal closingBalance = BigDecimal.ZERO;
        public BigDecimal totalIn = BigDecimal.ZERO;
        public BigDecimal totalOut = BigDecimal.ZERO;
        public List<Transaction> transactions = new ArrayList<>();
    }

    public List<Transaction> searchTransactions(String query, LocalDate start, LocalDate end) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        
        if (query != null && !query.trim().isEmpty()) {
            sql.append("AND (description LIKE ? OR type LIKE ? OR CAST(id AS TEXT) LIKE ?) ");
            String likeQuery = "%" + query.trim() + "%";
            params.add(likeQuery);
            params.add(likeQuery);
            params.add(likeQuery);
        }
        if (start != null) {
            sql.append("AND created_at >= ? ");
            params.add(Timestamp.valueOf(start.atStartOfDay()));
        }
        if (end != null) {
            sql.append("AND created_at < ? ");
            params.add(Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
        }
        sql.append("ORDER BY created_at DESC LIMIT 500");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(mapTransaction(rs));
            }
        }
        return list;
    }

    public CashbookSummary getCashbook(LocalDate date) throws SQLException {
        CashbookSummary summary = new CashbookSummary();
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Get opening balance (sum of DEPOSITS - WITHDRAWALS up to start of date)
            PreparedStatement openStmt = conn.prepareStatement(
                "SELECT SUM(CASE WHEN type LIKE '%DEPOSIT%' OR type LIKE '%CREDIT%' THEN amount ELSE -amount END) as bal " +
                "FROM transactions WHERE status = 'ACTIVE' AND created_at < ?");
            openStmt.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
            ResultSet rsOpen = openStmt.executeQuery();
            if (rsOpen.next() && rsOpen.getBigDecimal("bal") != null) {
                summary.openingBalance = rsOpen.getBigDecimal("bal");
            }

            // Get transactions for the day
            PreparedStatement txStmt = conn.prepareStatement(
                "SELECT * FROM transactions WHERE created_at >= ? AND created_at < ? ORDER BY created_at ASC");
            txStmt.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
            txStmt.setTimestamp(2, Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
            ResultSet rsTx = txStmt.executeQuery();
            
            while (rsTx.next()) {
                Transaction tx = mapTransaction(rsTx);
                summary.transactions.add(tx);
                if ("ACTIVE".equals(tx.getStatus())) {
                    if (tx.getType().contains("DEPOSIT") || tx.getType().contains("CREDIT")) {
                        summary.totalIn = summary.totalIn.add(tx.getAmount());
                    } else if (tx.getType().contains("WITHDRAWAL") || tx.getType().contains("DEBIT")) {
                        summary.totalOut = summary.totalOut.add(tx.getAmount());
                    }
                }
            }
            
            summary.closingBalance = summary.openingBalance.add(summary.totalIn).subtract(summary.totalOut);
        }
        return summary;
    }

    private Transaction mapTransaction(ResultSet rs) throws SQLException {
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
        return tx;
    }

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

    public List<Transaction> getTransactionsBetween(LocalDate start, LocalDate end) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM transactions WHERE created_at >= ? AND created_at < ? ORDER BY created_at DESC LIMIT 5000")) {
            stmt.setTimestamp(1, Timestamp.valueOf(start.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(mapTransaction(rs));
        }
        return list;
    }

    public List<Transaction> getTransactionsForAccount(int accountId, LocalDate start, LocalDate end) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM transactions WHERE account_id = ? ");
        if (start != null) sql.append("AND created_at >= ? ");
        if (end != null)   sql.append("AND created_at < ? ");
        sql.append("ORDER BY created_at ASC LIMIT 5000");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            stmt.setInt(idx++, accountId);
            if (start != null) stmt.setTimestamp(idx++, Timestamp.valueOf(start.atStartOfDay()));
            if (end != null)   stmt.setTimestamp(idx++, Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(mapTransaction(rs));
        }
        return list;
    }

    public List<Transaction> getRecent(int limit) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM transactions ORDER BY created_at DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(mapTransaction(rs));
            }
        }
        return list;
    }
}
