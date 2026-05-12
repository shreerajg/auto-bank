package com.autobank.auth.service;

import com.autobank.auth.model.User;
import com.autobank.config.DatabaseConfig;
import com.autobank.util.AuditLogger;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Optional;

public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public Optional<User> authenticate(String username, String password) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT id, username, password_hash, role, is_active " +
                 "FROM users WHERE username = ? AND is_active = true")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && BCrypt.checkpw(password, rs.getString("password_hash"))) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setRole(rs.getString("role"));
                user.setActive(rs.getBoolean("is_active"));
                logSession(conn, user.getId(), true);
                AuditLogger.log("LOGIN_SUCCESS", "USER", user.getId(),
                                "Login: " + username, user.getId());
                return Optional.of(user);
            }
        } catch (SQLException e) {
            log.error("Auth error", e);
        }
        AuditLogger.log("LOGIN_FAILED", "USER", null, "Failed login: " + username, null);
        return Optional.empty();
    }

    private void logSession(Connection conn, int userId, boolean success) {
        try (PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO login_sessions (user_id, status) VALUES (?, ?)")) {
            stmt.setInt(1, userId);
            stmt.setString(2, success ? "SUCCESS" : "FAILED");
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warn("Session log failed: {}", e.getMessage());
        }
    }
}
