package com.autobank.util;

import com.autobank.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audit-logger-thread");
        t.setDaemon(true);
        return t;
    });

    public static void log(String eventType, String entityType, Integer entityId,
                           String description, Integer operatorId) {
        if (!DatabaseConfig.isConnected()) return;

        executor.submit(() -> {
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
        });
    }

    /**
     * Flush pending audit log writes by shutting down the executor gracefully,
     * waiting up to 5 seconds for pending tasks to complete, then recreating
     * a fresh single-thread daemon executor.
     */
    public static void flushPendingLogs() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // Recreate a fresh executor for subsequent log calls
        ExecutorService fresh = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "audit-logger-thread");
            t.setDaemon(true);
            return t;
        });
        // Use reflection to replace the private static field since we cannot
        // reassign a final field directly — fall back to a new executor reference.
        try {
            java.lang.reflect.Field field = AuditLogger.class.getDeclaredField("executor");
            field.setAccessible(true);
            // Suppress unchecked warning for the reflective set
            field.set(null, fresh);
        } catch (Exception e) {
            log.warn("Failed to replace audit executor: {}", e.getMessage());
        }
    }

    /**
     * Check whether the audit log executor is still accepting tasks.
     */
    public static boolean isExecutorHealthy() {
        return !executor.isShutdown() && !executor.isTerminated();
    }
}
