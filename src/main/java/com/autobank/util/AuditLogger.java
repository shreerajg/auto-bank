package com.autobank.util;

import com.autobank.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;

public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    public static void log(String eventType, String entityType, Integer entityId,
                           String description, Integer operatorId) {
        if (!DatabaseConfig.isConnected()) return;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO audit_log (event_type, entity_type, entity_id, description, operator_id) " +
                 "VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, eventType);
            stmt.setString(2, entityType);
            if (entityId != null) stmt.setInt(3, entityId); else stmt.setNull(3, Types.INTEGER);
            stmt.setString(4, description);
            if (operatorId != null) stmt.setInt(5, operatorId); else stmt.setNull(5, Types.INTEGER);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.warn("Audit write failed: {} — {}", eventType, description);
        }
    }
}
