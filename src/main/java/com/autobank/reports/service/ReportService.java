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

    public String generateMemberBalanceReport() throws Exception {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("headers", new String[]{"Account #", "Holder Name", "Phone", "Balance (₹)", "Status"});
        
        List<List<String>> rows = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT account_number, holder_name, phone, balance, status FROM accounts ORDER BY holder_name")) {
            while (rs.next()) {
                rows.add(java.util.List.of(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3) != null ? rs.getString(3) : "-",
                    rs.getBigDecimal(4).toString(),
                    rs.getString(5)
                ));
            }
        }
        data.put("rows", rows);
        return runPythonReport(data, "Member_Balances", "Member Balance Report");
    }

    public String generateDailyTransactionReport(java.time.LocalDate date) throws Exception {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("headers", new String[]{"ID", "Account", "Type", "Amount (₹)", "Time", "Status"});
        
        List<List<String>> rows = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT t.id, a.account_number, t.type, t.amount, t.created_at, t.status " +
                 "FROM transactions t JOIN accounts a ON t.account_id = a.id " +
                 "WHERE DATE(t.created_at) = ? ORDER BY t.created_at")) {
            ps.setDate(1, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rows.add(java.util.List.of(
                    String.valueOf(rs.getInt(1)),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getBigDecimal(4).toString(),
                    rs.getTimestamp(5).toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                    rs.getString(6)
                ));
            }
        }
        data.put("rows", rows);
        return runPythonReport(data, "Daily_Transactions_" + date, "Daily Transaction Report (" + date + ")");
    }

    private String runPythonReport(Map<String, Object> data, String filePrefix, String title) throws Exception {
        // Create temp JSON file
        java.io.File tempJson = java.io.File.createTempFile("report_data_", ".json");
        try (java.io.FileWriter writer = new java.io.FileWriter(tempJson)) {
            new com.google.gson.Gson().toJson(data, writer);
        }

        String fileName = filePrefix + "_" + System.currentTimeMillis() + ".pdf";
        java.io.File outputDir = new java.io.File(System.getProperty("user.home"), "AutoBank/reports");
        if (!outputDir.exists()) outputDir.mkdirs();
        java.io.File outputFile = new java.io.File(outputDir, fileName);

        ProcessBuilder pb = new ProcessBuilder("python", "python/report_generator.py", 
                tempJson.getAbsolutePath(), outputFile.getAbsolutePath(), title);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line);
        }
        
        if (p.waitFor() != 0) throw new Exception("Python error: " + output.toString());
        
        tempJson.delete();
        return outputFile.getAbsolutePath();
    }
}

