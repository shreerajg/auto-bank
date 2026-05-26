package com.autobank.account.service;

import com.autobank.account.model.Account;
import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.autobank.util.AuditLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AccountService {

    public List<Account> searchAccounts(String query) throws SQLException {
        boolean blank = query == null || query.isBlank();
        String sql = blank
            ? "SELECT * FROM accounts ORDER BY holder_name LIMIT 200"
            : "SELECT * FROM accounts WHERE holder_name LIKE ? OR account_number LIKE ? OR phone LIKE ? ORDER BY holder_name LIMIT 100";

        List<Account> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!blank) {
                String like = "%" + query.trim() + "%";
                stmt.setString(1, like);
                stmt.setString(2, like);
                stmt.setString(3, like);
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Account createAccount(String holderName, String phone, String address) throws SQLException {
        // Generate a more robust account number: AC-Timestamp-Random
        long ts = System.currentTimeMillis() / 1000;
        int rnd = new java.util.Random().nextInt(900) + 100; // 3-digit random
        String number = "AC" + ts + rnd; 
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO accounts (account_number, holder_name, phone, address) " +
                 "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, number);
            stmt.setString(2, holderName);
            stmt.setString(3, (phone == null || phone.isBlank()) ? null : phone.trim());
            stmt.setString(4, (address == null || address.isBlank()) ? null : address.trim());
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    Account a = new Account();
                    a.setId(rs.getInt(1));
                    a.setAccountNumber(number);
                    a.setHolderName(holderName);
                    a.setPhone(phone);
                    a.setAddress(address);
                    a.setBalance(new java.math.BigDecimal("0.00"));
                    a.setStatus("ACTIVE");
                    a.setCreatedAt(java.time.LocalDateTime.now());
                    
                    com.autobank.auth.model.User user = com.autobank.auth.model.UserSession.getInstance().getCurrentUser();
                    Integer opId = (user != null) ? user.getId() : null;
                    
                    AuditLogger.log("ACCOUNT_CREATED", "ACCOUNT", a.getId(),
                                    "Account " + number + " for " + holderName, opId);
                    return a;
                }
            }
        }
        throw new SQLException("Account insert returned no rows");
    }

    public void updateAccount(Account a) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE accounts SET holder_name = ?, phone = ?, address = ?, status = ? WHERE id = ?")) {
            stmt.setString(1, a.getHolderName());
            stmt.setString(2, (a.getPhone() == null || a.getPhone().isBlank()) ? null : a.getPhone().trim());
            stmt.setString(3, (a.getAddress() == null || a.getAddress().isBlank()) ? null : a.getAddress().trim());
            stmt.setString(4, a.getStatus());
            stmt.setInt(5, a.getId());
            stmt.executeUpdate();

            com.autobank.auth.model.User user = com.autobank.auth.model.UserSession.getInstance().getCurrentUser();
            Integer opId = (user != null) ? user.getId() : null;

            AuditLogger.log("ACCOUNT_UPDATED", "ACCOUNT", a.getId(),
                            "Updated info for " + a.getAccountNumber(), opId);
        }
    }

    public void deactivateAccount(int id, String reason) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE accounts SET status = 'INACTIVE' WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();

            com.autobank.auth.model.User user = com.autobank.auth.model.UserSession.getInstance().getCurrentUser();
            Integer opId = (user != null) ? user.getId() : null;

            AuditLogger.log("ACCOUNT_DEACTIVATED", "ACCOUNT", id,
                            "Deactivated: " + reason, opId);
        }
    }



    private Account map(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setId(rs.getInt("id"));
        a.setAccountNumber(rs.getString("account_number"));
        a.setHolderName(rs.getString("holder_name"));
        a.setPhone(rs.getString("phone"));
        a.setAddress(rs.getString("address"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setInterestRate(rs.getBigDecimal("interest_rate"));
        a.setStatus(rs.getString("status"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) a.setCreatedAt(ts.toLocalDateTime());
        return a;
    }
}
