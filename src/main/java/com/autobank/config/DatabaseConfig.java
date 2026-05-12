package com.autobank.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Properties;

public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static HikariDataSource dataSource;
    private static String connectionError;

    static {
        try {
            Properties props = new Properties();
            InputStream in = DatabaseConfig.class.getResourceAsStream("/config.properties");
            if (in != null) {
                props.load(in);
                in.close();
            }

            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(props.getProperty("db.url", "jdbc:mysql://localhost:3306/autobank"));
            cfg.setUsername(props.getProperty("db.user", "root"));
            cfg.setPassword(props.getProperty("db.password", "topg"));
            cfg.setMaximumPoolSize(5);
            cfg.setConnectionTimeout(8000);
            cfg.setAutoCommit(true);

            dataSource = new HikariDataSource(cfg);
            log.info("Database pool ready");
        } catch (Exception e) {
            connectionError = e.getMessage();
            log.error("DB init failed: {}", e.getMessage());
        }
    }

    public static boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    public static String getConnectionError() {
        return connectionError;
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void runSchema() throws Exception {
        try (InputStream in = DatabaseConfig.class.getResourceAsStream("/schema.sql")) {
            if (in == null)
                return;
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conn = getConnection()) {
                for (String raw : sql.split(";")) {
                    // strip inline comments
                    String s = raw.replaceAll("--[^\n]*", "").strip();
                    if (!s.isEmpty()) {
                        try (Statement st = conn.createStatement()) {
                            st.execute(s);
                        } catch (SQLException ex) {
                            log.debug("Schema skip: {}", ex.getMessage());
                        }
                    }
                }
            }
        }
    }

    public static boolean isFirstRun() {
        try (Connection conn = getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() && rs.getInt(1) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("DB pool closed");
        }
    }
}
