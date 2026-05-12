package com.autobank.settings.ui;

import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.autobank.util.AuditLogger;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class SettingsController {

    @FXML private Label dbStatusLabel;
    @FXML private Label dbUrlLabel;
    @FXML private TextField dbUrlField;
    @FXML private TextField dbUserField;
    @FXML private PasswordField dbPasswordField;
    @FXML private Label settingsStatusLabel;

    @FXML private TextField oldPasswordField;
    @FXML private TextField newPasswordField;
    @FXML private TextField confirmPasswordField;
    @FXML private Label pwStatusLabel;

    @FXML private Label versionLabel;
    @FXML private Label currentUserLabel;
    @FXML private TextArea auditLogArea;

    @FXML
    public void initialize() {
        versionLabel.setText("AutoBank v1.0");
        var user = UserSession.getInstance().getCurrentUser();
        currentUserLabel.setText(user.getUsername() + " (" + user.getRole() + ")");

        // Load current config
        try {
            Properties props = new Properties();
            InputStream is = getClass().getResourceAsStream("/config.properties");
            if (is != null) props.load(is);
            dbUrlField.setText(props.getProperty("db.url", ""));
            dbUserField.setText(props.getProperty("db.user", ""));
        } catch (Exception ignored) {}

        refreshDbStatus();
        loadRecentAudit();
    }

    @FXML
    private void handleTestConnection() {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
            dbStatusLabel.setText("✓ Connected to database");
            dbStatusLabel.setStyle("-fx-text-fill: #27ae60;");
        } catch (Exception e) {
            dbStatusLabel.setText("✗ " + e.getMessage());
            dbStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    @FXML
    private void handleChangePassword() {
        String oldPw = oldPasswordField.getText().trim();
        String newPw = newPasswordField.getText().trim();
        String confirm = confirmPasswordField.getText().trim();

        if (oldPw.isEmpty() || newPw.isEmpty()) { pwStatus("Fill all fields"); return; }
        if (!newPw.equals(confirm)) { pwStatus("Passwords do not match"); return; }
        if (newPw.length() < 4) { pwStatus("Password must be at least 4 chars"); return; }

        try (Connection conn = DatabaseConfig.getConnection()) {
            int userId = UserSession.getInstance().getCurrentUser().getId();
            PreparedStatement check = conn.prepareStatement(
                "SELECT password_hash FROM users WHERE id = ?");
            check.setInt(1, userId);
            ResultSet rs = check.executeQuery();
            if (!rs.next()) { pwStatus("User not found"); return; }
            String hash = rs.getString("password_hash");
            if (!org.mindrot.jbcrypt.BCrypt.checkpw(oldPw, hash)) {
                pwStatus("Current password is incorrect");
                return;
            }
            String newHash = org.mindrot.jbcrypt.BCrypt.hashpw(newPw, org.mindrot.jbcrypt.BCrypt.gensalt());
            PreparedStatement upd = conn.prepareStatement(
                "UPDATE users SET password_hash = ? WHERE id = ?");
            upd.setString(1, newHash);
            upd.setInt(2, userId);
            upd.executeUpdate();
            AuditLogger.log("PASSWORD_CHANGED", "USER", userId, "Password changed", userId);
            pwStatus("✓ Password changed successfully");
            oldPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
        } catch (Exception e) {
            pwStatus("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleViewFullLog() {
        loadRecentAudit();
    }

    private void loadRecentAudit() {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT created_at, event_type, entity_type, description " +
                 "FROM audit_log ORDER BY created_at DESC LIMIT 50")) {
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                sb.append(rs.getTimestamp("created_at")).append(" | ")
                  .append(rs.getString("event_type")).append(" | ")
                  .append(rs.getString("entity_type")).append(" | ")
                  .append(rs.getString("description")).append("\n");
            }
            auditLogArea.setText(sb.toString());
        } catch (Exception e) {
            auditLogArea.setText("Could not load audit log: " + e.getMessage());
        }
    }

    private void refreshDbStatus() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            dbStatusLabel.setText("✓ Connected");
            dbStatusLabel.setStyle("-fx-text-fill: #27ae60;");
        } catch (Exception e) {
            dbStatusLabel.setText("✗ Not connected");
            dbStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    private void pwStatus(String msg) { pwStatusLabel.setText(msg); }
}
