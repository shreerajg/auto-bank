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
        Label lbl = new Label(name + "\n— Coming Soon —");
        lbl.setStyle("-fx-font-size: 22px; -fx-text-fill: #999; -fx-text-alignment: center;");
        contentArea.getChildren().setAll(lbl);
    }

    private void activate(Button active) {
        for (Button b : new Button[]{dashboardBtn, accountsBtn, transactionsBtn, distributionBtn,
                                     loansBtn, dailyOpsBtn, reportsBtn, settingsBtn, backupBtn}) {
            b.getStyleClass().remove("active");
        }
        active.getStyleClass().add("active");
    }
}
