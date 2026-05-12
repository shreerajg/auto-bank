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
        BigDecimal totalBalance,
        int closedLoans,
        int todayTransactions,
        BigDecimal todayVolume
    ) {}

    public SummaryStats getSummary() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            int accounts      = queryInt(conn, "SELECT COUNT(*) FROM accounts WHERE status='ACTIVE'");
            BigDecimal deps   = queryDecimal(conn, "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='DEPOSIT' AND status='ACTIVE'");
            BigDecimal withs  = queryDecimal(conn, "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type='WITHDRAWAL' AND status='ACTIVE'");
            int txCount       = queryInt(conn, "SELECT COUNT(*) FROM transactions WHERE status='ACTIVE'");
            int activeLoans   = queryInt(conn, "SELECT COUNT(*) FROM loans WHERE status='ACTIVE'");
            int closedLoans   = queryInt(conn, "SELECT COUNT(*) FROM loans WHERE status='CLOSED'");
            BigDecimal outstanding = queryDecimal(conn, "SELECT COALESCE(SUM(outstanding),0) FROM loans WHERE status='ACTIVE'");
            BigDecimal totalBal   = queryDecimal(conn, "SELECT COALESCE(SUM(balance),0) FROM accounts WHERE status='ACTIVE'");
            int todayTx = queryInt(conn, "SELECT COUNT(*) FROM transactions WHERE DATE(created_at)=CURDATE() AND status='ACTIVE'");
            BigDecimal todayVol = queryDecimal(conn, "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE DATE(created_at)=CURDATE() AND status='ACTIVE'");
            return new SummaryStats(accounts, deps, withs, txCount, activeLoans, outstanding, totalBal, closedLoans, todayTx, todayVol);
        }
    }

    /** Daily deposit/withdrawal volumes over last N days */
    public Map<String, BigDecimal[]> getDailyVolume(int days) throws SQLException {
        Map<String, BigDecimal[]> map = new LinkedHashMap<>();
        String sql = "SELECT DATE(created_at) as d, type, COALESCE(SUM(amount),0) as total " +
                     "FROM transactions WHERE status='ACTIVE' " +
                     "AND created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
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

    /** Monthly total volume (last 6 months) */
    public Map<String, BigDecimal> getMonthlyVolume(int months) throws SQLException {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        String sql = "SELECT DATE_FORMAT(created_at, '%b %Y') as m, COALESCE(SUM(amount),0) as total " +
                     "FROM transactions WHERE status='ACTIVE' " +
                     "AND created_at >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) " +
                     "GROUP BY YEAR(created_at), MONTH(created_at), DATE_FORMAT(created_at,'%b %Y') " +
                     "ORDER BY YEAR(created_at), MONTH(created_at)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, months);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString("m"), rs.getBigDecimal("total"));
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
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private BigDecimal queryDecimal(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }
}
