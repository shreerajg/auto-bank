package com.autobank;

import com.autobank.auth.model.UserSession;
import com.autobank.backup.BackupScheduler;
import com.autobank.config.DatabaseConfig;
import com.autobank.util.I18n;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Properties;

public class AutoBankApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(AutoBankApp.class);

    @Override
    public void start(Stage stage) throws IOException {
        I18n.load("en");

        if (!DatabaseConfig.isConnected()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Cannot connect to MySQL");
            alert.setContentText(
                "Ensure MySQL is running and update:\n" +
                "src/main/resources/config.properties\n\n" +
                "Error: " + DatabaseConfig.getConnectionError()
            );
            alert.showAndWait();
            Platform.exit();
            return;
        }

        try {
            DatabaseConfig.runSchema();
        } catch (Exception e) {
            log.error("Schema setup failed", e);
        }

        if (DatabaseConfig.isFirstRun()) {
            setupAdmin();
        }

        showLogin(stage);

        // Configure and arm the daily backup scheduler from config
        initBackupScheduler();
    }

    private void initBackupScheduler() {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {}

        String schedule = props.getProperty("backup.schedule", "22:00").trim();
        try {
            String[] parts = schedule.split(":");
            LocalTime t = LocalTime.of(
                Integer.parseInt(parts[0]),
                parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
            BackupScheduler.getInstance().setScheduledTime(t);
        } catch (Exception e) {
            log.warn("Invalid backup.schedule '{}', using default 22:00", schedule);
        }
        // Scheduler is started in MainController once the user logs in
        log.info("Backup scheduler configured for {}", BackupScheduler.getInstance().getScheduledTime());
    }

    private void setupAdmin() {
        TextInputDialog dialog = new TextInputDialog("admin123");
        dialog.setTitle("First Run Setup");
        dialog.setHeaderText("No admin account found. Create one now.");
        dialog.setContentText("Admin password (min 4 chars):");
        Optional<String> result = dialog.showAndWait();
        String password = result.orElse("admin123");
        if (password.length() < 4) password = "admin123";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO users (username, password_hash, role) VALUES ('admin', ?, 'ADMIN')")) {
            stmt.setString(1, BCrypt.hashpw(password, BCrypt.gensalt(12)));
            stmt.executeUpdate();
            log.info("Admin user created");

            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Setup Complete");
            ok.setHeaderText("Admin account created");
            ok.setContentText("Username: admin\nPassword: " + password);
            ok.showAndWait();
        } catch (Exception e) {
            log.error("Failed to create admin user", e);
        }
    }

    private void showLogin(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        loader.setResources(I18n.getBundle());
        Scene scene = new Scene(loader.load(), 860, 540);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setTitle("AutoBank — Cooperative Banking System");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(760);
        stage.setMinHeight(480);
        stage.show();
    }

    @Override
    public void stop() {
        BackupScheduler.getInstance().stop();
        DatabaseConfig.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
