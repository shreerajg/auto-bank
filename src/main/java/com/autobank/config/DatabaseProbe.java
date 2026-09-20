package com.autobank.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Lightweight connectivity and version probe for the AutoBank database.
 * Exposes readiness / liveness-style checks that the UI and startup
 * flow can use without touching business services.
 */
public final class DatabaseProbe {

    private static final Logger log = LoggerFactory.getLogger(DatabaseProbe.class);
    private static final ThreadLocal<Connection> probeConnection = new ThreadLocal<>();

    private DatabaseProbe() {
        // utility class
    }

    /**
     * Check whether the Hikari pool is alive and can hand out a connection.
     */
    public static boolean isPoolAlive() {
        return DatabaseConfig.isConnected();
    }

    /**
     * Ping the database: open a connection, execute a cheap query, and
     * return true only if the database is reachable and responsive.
     */
    public static boolean ping() {
        if (!DatabaseConfig.isConnected()) {
            log.warn("Ping failed: pool is not connected");
            return false;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (SQLException e) {
            log.warn("Ping failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Return a short human-readable status string for display in the
     * settings screen (DB URL + connection state).
     */
    public static String getConnectionStatusSummary() {
        if (!DatabaseConfig.isConnected()) {
            String err = DatabaseConfig.getConnectionError();
            return "DISCONNECTED" + (err != null ? " — " + err : "");
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            String url = conn.getMetaData().getURL();
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            String version = conn.getMetaData().getDatabaseProductVersion();
            return dbProduct + " " + version + " @ " + url;
        } catch (SQLException e) {
            return "CONNECTED (metadata unavailable: " + e.getMessage() + ")";
        }
    }

    /**
     * Probe and return the current database version string.
     * Returns null if the database cannot be reached.
     */
    public static String getDatabaseVersion() {
        if (!ping()) {
            return null;
        }
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT VERSION()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            log.warn("Failed to read DB version: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check whether the required AutoBank schema tables exist.
     * Useful for first-run detection and migration gating.
     */
    public static boolean hasRequiredTables() {
        if (!ping()) {
            return false;
        }
        String[] required = {
                "users", "accounts", "transactions", "loans",
                "loan_payments", "daily_sessions", "payment_distributions",
                "distribution_records", "audit_log", "interest_batches"
        };
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            for (String table : required) {
                boolean found = false;
                // Reuse the same statement with a fresh query for each table
                try (var ts = conn.createStatement();
                     var trs = ts.executeQuery(
                             "SELECT COUNT(*) AS cnt FROM INFORMATION_SCHEMA.TABLES " +
                             "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "'")) {
                    if (trs.next() && trs.getInt("cnt") > 0) {
                        found = true;
                    }
                }
                if (!found) {
                    log.warn("Required table missing: {}", table);
                    return false;
                }
            }
            return true;
        } catch (SQLException e) {
            log.warn("Schema probe failed: {}", e.getMessage());
            return false;
        }
    }
}
