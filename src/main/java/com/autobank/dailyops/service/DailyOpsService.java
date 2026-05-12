package com.autobank.dailyops.service;

import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.autobank.dailyops.model.DailySession;
import com.autobank.util.AuditLogger;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DailyOpsService {

    public DailySession getTodaySession() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM daily_sessions WHERE date = ?")) {
            ps.setDate(1, Date.valueOf(LocalDate.now()));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);
        }
        return null;
    }

    public DailySession openDay(BigDecimal openingBalance) throws SQLException {
        int opId = UserSession.getInstance().getCurrentUser().getId();
        // Calculate total balances in all accounts as expected cash
        BigDecimal expected = getTotalAccountBalances();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO daily_sessions (date, opening_balance, expected_cash, status, opened_by, opened_at) " +
                     "VALUES (?, ?, ?, 'OPEN', ?, NOW())", Statement.RETURN_GENERATED_KEYS)) {
            ps.setDate(1, Date.valueOf(LocalDate.now()));
            ps.setBigDecimal(2, openingBalance);
            ps.setBigDecimal(3, expected);
            ps.setInt(4, opId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            DailySession s = new DailySession();
            if (rs.next()) s.setId(rs.getInt(1));
            s.setDate(LocalDate.now());
            s.setOpeningBalance(openingBalance);
            s.setExpectedCash(expected);
            s.setStatus("OPEN");
            AuditLogger.log("DAY_OPENED", "DAILY_SESSION", s.getId(),
                    "Day opened with balance ₹" + openingBalance, opId);
            return s;
        }
    }

    public void closeDay(int sessionId, BigDecimal actualCash) throws SQLException {
        int opId = UserSession.getInstance().getCurrentUser().getId();
        BigDecimal expected = getTotalAccountBalances();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE daily_sessions SET status='CLOSED', closing_balance=?, expected_cash=?, " +
                     "actual_cash=?, closed_by=?, closed_at=NOW() WHERE id=?")) {
            ps.setBigDecimal(1, expected);
            ps.setBigDecimal(2, expected);
            ps.setBigDecimal(3, actualCash);
            ps.setInt(4, opId);
            ps.setInt(5, sessionId);
            ps.executeUpdate();
            AuditLogger.log("DAY_CLOSED", "DAILY_SESSION", sessionId,
                    "Day closed. Actual cash: ₹" + actualCash + " | Expected: ₹" + expected, opId);
        }
    }

    private BigDecimal getTotalAccountBalances() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(SUM(balance),0) FROM accounts WHERE status='ACTIVE'")) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public List<DailySession> getRecentSessions(int limit) throws SQLException {
        List<DailySession> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM daily_sessions ORDER BY date DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private DailySession map(ResultSet rs) throws SQLException {
        DailySession s = new DailySession();
        s.setId(rs.getInt("id"));
        Date d = rs.getDate("date");
        if (d != null) s.setDate(d.toLocalDate());
        s.setOpeningBalance(rs.getBigDecimal("opening_balance"));
        s.setClosingBalance(rs.getBigDecimal("closing_balance"));
        s.setExpectedCash(rs.getBigDecimal("expected_cash"));
        s.setActualCash(rs.getBigDecimal("actual_cash"));
        s.setStatus(rs.getString("status"));
        Timestamp oa = rs.getTimestamp("opened_at");
        if (oa != null) s.setOpenedAt(oa.toLocalDateTime());
        Timestamp ca = rs.getTimestamp("closed_at");
        if (ca != null) s.setClosedAt(ca.toLocalDateTime());
        return s;
    }
}
