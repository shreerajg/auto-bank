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
            : "SELECT * FROM accounts WHERE holder_name ILIKE ? OR account_number ILIKE ? ORDER BY holder_name LIMIT 100";

        List<Account> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!blank) {
                String like = "%" + query.trim() + "%";
                stmt.setString(1, like);
                stmt.setString(2, like);
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public Account createAccount(String holderName, String phone, String address) throws SQLException {
        String number = "ACC" + System.currentTimeMillis();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO accounts (account_number, holder_name, phone, address) " +
                 "VALUES (?, ?, ?, ?) RETURNING *")) {
            stmt.setString(1, number);
            stmt.setString(2, holderName);
            stmt.setString(3, phone);
            stmt.setString(4, address);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Account a = map(rs);
                int opId = UserSession.getInstance().getCurrentUser().getId();
                AuditLogger.log("ACCOUNT_CREATED", "ACCOUNT", a.getId(),
                                "Account " + number + " for " + holderName, opId);
                return a;
            }
        }
        throw new SQLException("Account insert returned no rows");
    }

    private Account map(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setId(rs.getInt("id"));
        a.setAccountNumber(rs.getString("account_number"));
        a.setHolderName(rs.getString("holder_name"));
        a.setPhone(rs.getString("phone"));
        a.setAddress(rs.getString("address"));
        a.setBalance(rs.getBigDecimal("balance"));
        a.setStatus(rs.getString("status"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) a.setCreatedAt(ts.toLocalDateTime());
        return a;
    }
}
