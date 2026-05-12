package com.autobank.reports.service;

import com.autobank.config.DatabaseConfig;

import java.math.BigDecimal;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReportService {

    public record SummaryStats(
        int totalAccounts,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        int totalTransactions,
        int activeLoans,
        BigDecimal loanOutstanding,
        BigDecimal totalBalance
    ) {}

    public SummaryStats getSummary() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            int accounts = queryInt(conn, "SELECT COUNT(*) FROM accounts WHERE status='ACTIVE'");
            BigDecimal deposits = queryDecimal(conn,
                "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='DEPOSIT' AND status='ACTIVE'");
            BigDecimal withdrawals = queryDecimal(conn,
                "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='WITHDRAWAL' AND status='ACTIVE'");
            int txCount = queryInt(conn, "SELECT COUNT(*) FROM transactions WHERE status='ACTIVE'");
            int activeLoans = queryInt(conn, "SELECT COUNT(*) FROM loans WHERE status='ACTIVE'");
            BigDecimal outstanding = queryDecimal(conn,
                "SELECT COALESCE(SUM(outstanding),0) FROM loans WHERE status='ACTIVE'");
            BigDecimal totalBal = queryDecimal(conn,
                "SELECT COALESCE(SUM(balance),0) FROM accounts WHERE status='ACTIVE'");
            return new SummaryStats(accounts, deposits, withdrawals, txCount, activeLoans, outstanding, totalBal);
        }
    }

    /** Returns last N days of daily transaction volume: date -> (deposits, withdrawals) */
    public Map<String, BigDecimal[]> getDailyVolume(int days) throws SQLException {
        Map<String, BigDecimal[]> map = new LinkedHashMap<>();
        String sql = "SELECT DATE(created_at) as d, type, COALESCE(SUM(amount),0) as total " +
                     "FROM transactions WHERE status='ACTIVE' " +
                     "AND created_at >= DATE_SUB(NOW(), INTERVAL ? DAY) " +
                     "GROUP BY DATE(created_at), type ORDER BY d";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, days);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String date = rs.getString("d");
                String type = rs.getString("type");
                BigDecimal amount = rs.getBigDecimal("total");
                map.computeIfAbsent(date, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                if ("DEPOSIT".equals(type))    map.get(date)[0] = amount;
                if ("WITHDRAWAL".equals(type)) map.get(date)[1] = amount;
            }
        }
        return map;
    }

    /** Top N accounts by balance */
    public ResultSet getTopAccounts(Connection conn, int n) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT account_number, holder_name, balance FROM accounts " +
            "WHERE status='ACTIVE' ORDER BY balance DESC LIMIT ?");
        ps.setInt(1, n);
        return ps.executeQuery();
    }

    private int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private BigDecimal queryDecimal(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }
}
