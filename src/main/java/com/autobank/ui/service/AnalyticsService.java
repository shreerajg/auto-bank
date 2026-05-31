package com.autobank.ui.service;

import com.autobank.config.DatabaseConfig;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsService {

    public static class CashFlowData {
        public String month;
        public BigDecimal deposits;
        public BigDecimal withdrawals;

        public CashFlowData(String month, BigDecimal deposits, BigDecimal withdrawals) {
            this.month = month;
            this.deposits = deposits;
            this.withdrawals = withdrawals;
        }
    }

    public List<CashFlowData> getMonthlyCashFlow(int months) throws SQLException {
        List<CashFlowData> result = new ArrayList<>();
        String sql = "SELECT DATE_FORMAT(created_at, '%Y-%m') as mo, " +
                     "COALESCE(SUM(CASE WHEN type = 'DEPOSIT' THEN amount ELSE 0 END), 0) as dep, " +
                     "COALESCE(SUM(CASE WHEN type = 'WITHDRAWAL' THEN amount ELSE 0 END), 0) as wth " +
                     "FROM transactions " +
                     "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) " +
                     "GROUP BY mo ORDER BY mo";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, months);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new CashFlowData(
                    rs.getString("mo"),
                    rs.getBigDecimal("dep"),
                    rs.getBigDecimal("wth")
                ));
            }
        }
        return result;
    }

    public Map<String, Integer> getLoanDistribution() throws SQLException {
        Map<String, Integer> result = new LinkedHashMap<>();
        String sql = "SELECT status, COUNT(*) as c FROM loans GROUP BY status";

        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString("status"), rs.getInt("c"));
            }
        }
        return result;
    }

    public Map<String, Integer> getAccountGrowth(int months) throws SQLException {
        Map<String, Integer> result = new LinkedHashMap<>();
        String sql = "SELECT DATE_FORMAT(created_at, '%Y-%m') as mo, COUNT(*) as c " +
                     "FROM accounts " +
                     "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) " +
                     "GROUP BY mo ORDER BY mo";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, months);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.put(rs.getString("mo"), rs.getInt("c"));
            }
        }
        return result;
    }
}
