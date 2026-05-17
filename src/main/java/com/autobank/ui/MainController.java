package com.autobank.ui;

import com.autobank.auth.model.UserSession;
import com.autobank.backup.BackupScheduler;
import com.autobank.util.AuditLogger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Label currentUserLabel;
    @FXML private Label dateLabel;
    @FXML private Button dashboardBtn;
    @FXML private Button accountsBtn;
    @FXML private Button transactionsBtn;
    @FXML private Button distributionBtn;
    @FXML private Button loansBtn;
    @FXML private Button dailyOpsBtn;
    @FXML private Button reportsBtn;
    @FXML private Button settingsBtn;
    @FXML private Button backupBtn;

    @FXML
    public void initialize() {
        var user = UserSession.getInstance().getCurrentUser();
        currentUserLabel.setText(user.getUsername() + " (" + user.getRole() + ")");
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        showDashboard();

        // Start the daily backup scheduler in the background
        BackupScheduler.getInstance().start(
            path -> {}, // silent success — backup is in ~/AutoBank/backups
            err  -> {}  // logged by BackupService itself
        );
    }

    @FXML private void showDashboard()    { load("/fxml/dashboard.fxml");     activate(dashboardBtn); }
    @FXML private void showAccounts()     { load("/fxml/accounts.fxml");      activate(accountsBtn); }
    @FXML private void showTransactions() { load("/fxml/transactions.fxml");  activate(transactionsBtn); }
    @FXML private void showDistributions(){ load("/fxml/distributions.fxml"); activate(distributionBtn); }
    @FXML private void showLoans()        { load("/fxml/loans.fxml");      activate(loansBtn); }
    @FXML private void showDailyOps()     { load("/fxml/dailyops.fxml");   activate(dailyOpsBtn); }
    @FXML private void showReports()      { load("/fxml/reports.fxml");    activate(reportsBtn); }
    @FXML private void showSettings()     { load("/fxml/settings.fxml");   activate(settingsBtn); }
    @FXML private void showBackup()        { load("/fxml/backup.fxml");      activate(backupBtn); }

    @FXML
    private void handleLogout() {
        var user = UserSession.getInstance().getCurrentUser();
        AuditLogger.log("LOGOUT", "USER", user.getId(), "Logout: " + user.getUsername(), user.getId());
        UserSession.getInstance().logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load(), 420, 520);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setResizable(false);
            stage.setTitle("AutoBank");
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void load(String fxml) {
        try {
            Node view = FXMLLoader.load(getClass().getResource(fxml));
            contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            placeholder(fxml + " — error: " + e.getMessage());
        }
    }

    private void placeholder(String name) {
        VBox box = new VBox(20);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.setStyle("-fx-background-color: white; -fx-padding: 40; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 20, 0, 0, 10);");
        box.setMaxSize(450, 300);
        
        Label icon = new Label("🚀");
        icon.setStyle("-fx-font-size: 48px;");
        
        Label title = new Label(name);
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        
        Label sub = new Label("This module is part of the AutoBank Premium suite.\nWe are currently finalizing its implementation.");
        sub.setStyle("-fx-text-alignment: center; -fx-text-fill: #64748b; -fx-font-size: 14px;");
        sub.setWrapText(true);
        
        box.getChildren().addAll(icon, title, sub);
        
        StackPane wrapper = new StackPane(box);
        wrapper.setStyle("-fx-background-color: #f8fafc;");
        contentArea.getChildren().setAll(wrapper);
    }

    private void activate(Button active) {
        for (Button b : new Button[]{dashboardBtn, accountsBtn, transactionsBtn, distributionBtn,
                                     loansBtn, dailyOpsBtn, reportsBtn, settingsBtn, backupBtn}) {
            b.getStyleClass().remove("active");
        }
        active.getStyleClass().add("active");
    }
}
