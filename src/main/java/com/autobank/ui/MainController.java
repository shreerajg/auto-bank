package com.autobank.ui;

import com.autobank.auth.model.UserSession;
import com.autobank.util.AuditLogger;
import com.autobank.util.I18n;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
    @FXML private Button langBtn;
    @FXML private TextField globalSearchField;

    private static String pendingSearchQuery = null;
    private String currentViewFxml = "/fxml/dashboard.fxml";
    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        var user = UserSession.getInstance().getCurrentUser();
        currentUserLabel.setText(user.getUsername() + " (" + user.getRole() + ")");
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        
        refreshLabels();
        showDashboard();
    }

    private void refreshLabels() {
        dashboardBtn.setText("📊  " + I18n.t("nav.dashboard"));
        accountsBtn.setText("👤  " + I18n.t("nav.accounts"));
        transactionsBtn.setText("💸  " + I18n.t("nav.transactions"));
        interestBtn.setText("📈  " + I18n.t("nav.interest"));
        distributionBtn.setText("🥛  " + I18n.t("nav.distributions"));
        loansBtn.setText("🏠  " + I18n.t("nav.loans"));
        dailyOpsBtn.setText("📅  " + I18n.t("nav.dailyops"));
        reportsBtn.setText("📈  " + I18n.t("nav.reports"));
        backupBtn.setText("🔒  " + I18n.t("nav.backup"));
        settingsBtn.setText("⚙  " + I18n.t("nav.settings"));
        langBtn.setText(I18n.getCurrentLang().equals("en") ? "मराठी" : "English");
    }

    @FXML
    private void toggleLanguage() {
        String newLang = I18n.getCurrentLang().equals("en") ? "mr" : "en";
        I18n.load(newLang);
        refreshLabels();
        
        // Reload current view to apply translations
        if (currentViewFxml != null) {
            load(currentViewFxml);
        } else if (currentActiveBtn != null) {
            // If it's a placeholder, just call the show method again
            if (currentActiveBtn == loansBtn) showLoans();
            else if (currentActiveBtn == dailyOpsBtn) showDailyOps();
            else if (currentActiveBtn == reportsBtn) showReports();
            else if (currentActiveBtn == settingsBtn) showSettings();
            else if (currentActiveBtn == backupBtn) showBackup();
        }
    }

    @FXML private void showDashboard()    { currentViewFxml = "/fxml/dashboard.fxml"; load(currentViewFxml); activate(dashboardBtn); }
    @FXML private void showAccounts()     { currentViewFxml = "/fxml/accounts.fxml";  load(currentViewFxml); activate(accountsBtn); }
    @FXML private void showTransactions() { currentViewFxml = "/fxml/transactions.fxml"; load(currentViewFxml); activate(transactionsBtn); }
    @FXML private void showDistributions(){ currentViewFxml = "/fxml/distributions.fxml"; load(currentViewFxml); activate(distributionBtn); }
    @FXML private void showLoans()        { currentViewFxml = "/fxml/loans.fxml";       load(currentViewFxml); activate(loansBtn); }
    @FXML private void showDailyOps()     { currentViewFxml = "/fxml/dailyops.fxml";   load(currentViewFxml); activate(dailyOpsBtn); }
    @FXML private void showReports()      { currentViewFxml = "/fxml/reports.fxml";    load(currentViewFxml); activate(reportsBtn); }
    @FXML private void showSettings()     { currentViewFxml = "/fxml/settings.fxml";   load(currentViewFxml); activate(settingsBtn); }
    @FXML private void showBackup()       { currentViewFxml = "/fxml/backup.fxml";     load(currentViewFxml); activate(backupBtn); }

    @FXML
    private void handleLogout() {
        var user = UserSession.getInstance().getCurrentUser();
        AuditLogger.log("LOGOUT", "USER", user.getId(), "Logout: " + user.getUsername(), user.getId());
        UserSession.getInstance().logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load(), 1000, 650);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setResizable(false);
            stage.setTitle("AutoBank");
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleGlobalSearch() {
        String query = globalSearchField.getText();
        if (query != null && !query.isBlank()) {
            pendingSearchQuery = query.trim();
            showAccounts();
            globalSearchField.clear();
        }
    }

    public static String getPendingSearchQuery() {
        String q = pendingSearchQuery;
        pendingSearchQuery = null;
        return q;
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
        currentActiveBtn = active;
        for (Button b : new Button[]{dashboardBtn, accountsBtn, transactionsBtn, distributionBtn,
                                     loansBtn, dailyOpsBtn, reportsBtn, settingsBtn, backupBtn}) {
            if (b != null) b.getStyleClass().remove("active");
        }
        if (active != null) active.getStyleClass().add("active");
    }
}
       if (active != null) active.getStyleClass().add("active");
    }
}
