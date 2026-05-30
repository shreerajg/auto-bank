package com.autobank.ui;

import com.autobank.auth.model.UserSession;
import com.autobank.util.AuditLogger;
import com.autobank.util.I18n;
import com.autobank.util.Toast;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.prefs.Preferences;

public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML private StackPane contentArea;
    @FXML private Label currentUserLabel;
    @FXML private Label dateLabel;
    @FXML private Label viewTitleLabel;
    @FXML private Label viewSubtitleLabel;
    
    @FXML private Button dashboardBtn;
    @FXML private Button accountsBtn;
    @FXML private Button transactionsBtn;
    @FXML private Button interestBtn;
    @FXML private Button distributionBtn;
    @FXML private Button loansBtn;
    @FXML private Button dailyOpsBtn;
    @FXML private Button reportsBtn;
    @FXML private Button backupBtn;
    @FXML private Button settingsBtn;
    
    @FXML private Button langBtn;
    @FXML private Button themeBtn;
    @FXML private TextField globalSearchField;

    private static StackPane staticContentArea;
    private static String pendingSearchQuery = null;
    private String currentViewFxml = "/fxml/dashboard.fxml";
    private Button currentActiveBtn;
    
    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);
    private String currentTheme = "light";
    private static MainController instance;

    @FXML
    public void initialize() {
        instance = this;
        staticContentArea = contentArea;
        var user = UserSession.getInstance().getCurrentUser();
        currentUserLabel.setText(user.getUsername() + " (" + user.getRole() + ")");
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));

        loadTheme();
        refreshLabels();
        showDashboard();
        setupShortcuts();
    }

    public static void setTheme(String themeName) {
        if (instance != null) {
            instance.currentTheme = themeName;
            instance.prefs.put("theme", themeName);
            instance.applyTheme();
        }
    }

    private void loadTheme() {
        currentTheme = prefs.get("theme", "light");
        applyTheme();
    }

    @FXML
    private void cycleTheme() {
        switch (currentTheme) {
            case "light": currentTheme = "dark"; break;
            case "dark":  currentTheme = "blue"; break;
            case "blue":  currentTheme = "light"; break;
            default:      currentTheme = "light";
        }
        prefs.put("theme", currentTheme);
        applyTheme();
        Toast.show(contentArea, "Theme: " + currentTheme.toUpperCase());
    }

    private void applyTheme() {
        Platform.runLater(() -> {
            if (contentArea.getScene() == null) return;
            var root = contentArea.getScene().getRoot();
            root.getStyleClass().removeAll("theme-dark", "theme-blue");
            if (!currentTheme.equals("light")) {
                root.getStyleClass().add("theme-" + currentTheme);
            }
        });
    }

    public static void showToast(String message, Toast.Type type) {
        if (staticContentArea != null) {
            switch (type) {
                case SUCCESS: Toast.success(staticContentArea, message); break;
                case ERROR:   Toast.error(staticContentArea, message); break;
                case INFO:    Toast.show(staticContentArea, message); break;
            }
        }
    }

    private void setupShortcuts() {
        contentArea.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.isControlDown() && event.getCode() == KeyCode.F) {
                        globalSearchField.requestFocus();
                        event.consume();
                    } else if (event.isControlDown() && event.getCode() == KeyCode.K) {
                        showCommandPalette();
                        event.consume();
                    } else if (event.isAltDown()) {
                        switch (event.getCode()) {
                            case DIGIT1: showDashboard(); event.consume(); break;
                            case DIGIT2: showAccounts(); event.consume(); break;
                            case DIGIT3: showTransactions(); event.consume(); break;
                            case DIGIT4: showInterest(); event.consume(); break;
                            case DIGIT5: showDistributions(); event.consume(); break;
                            case DIGIT6: showLoans(); event.consume(); break;
                            case DIGIT7: showDailyOps(); event.consume(); break;
                            case DIGIT8: showReports(); event.consume(); break;
                            case DIGIT9: showBackup(); event.consume(); break;
                            case DIGIT0: showSettings(); event.consume(); break;
                        }
                    } else if (event.isControlDown() && event.getCode() == KeyCode.L) {
                        toggleLanguage();
                        event.consume();
                    }
                });
            }
        });
    }

    private void showCommandPalette() {
        CommandPalette palette = new CommandPalette(this);
        palette.show(staticContentArea);
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

        globalSearchField.setPromptText(I18n.t("search.global.prompt"));
        
        // Update date with locale-aware formatting if needed, or just refresh
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", 
            I18n.getCurrentLang().equals("mr") ? new java.util.Locale("mr", "IN") : java.util.Locale.ENGLISH)));

        // Update titles for current view
        updateHeaderTitles();
    }

    private void updateHeaderTitles() {
        if (currentViewFxml == null) return;
        switch (currentViewFxml) {
            case "/fxml/dashboard.fxml":
                viewTitleLabel.setText(I18n.t("nav.dashboard"));
                viewSubtitleLabel.setText("System overview and performance metrics");
                break;
            case "/fxml/accounts.fxml":
                viewTitleLabel.setText(I18n.t("nav.accounts"));
                viewSubtitleLabel.setText("Manage customer profiles and bank accounts");
                break;
            case "/fxml/transactions.fxml":
                viewTitleLabel.setText(I18n.t("nav.transactions"));
                viewSubtitleLabel.setText("Execute deposits, withdrawals and transfers");
                break;
            case "/fxml/interest.fxml":
                viewTitleLabel.setText(I18n.t("nav.interest"));
                viewSubtitleLabel.setText("Calculate and apply periodic interest");
                break;
            case "/fxml/distributions.fxml":
                viewTitleLabel.setText(I18n.t("nav.distributions"));
                viewSubtitleLabel.setText("Process dairy payments and rural distributions");
                break;
            case "/fxml/loans.fxml":
                viewTitleLabel.setText(I18n.t("nav.loans"));
                viewSubtitleLabel.setText("Track and manage loan disbursements and EMI");
                break;
            case "/fxml/dailyops.fxml":
                viewTitleLabel.setText(I18n.t("nav.dailyops"));
                viewSubtitleLabel.setText("Day opening/closing and cashbook verification");
                break;
            case "/fxml/reports.fxml":
                viewTitleLabel.setText(I18n.t("nav.reports"));
                viewSubtitleLabel.setText("Generate financial statements and audit reports");
                break;
            case "/fxml/backup.fxml":
                viewTitleLabel.setText(I18n.t("nav.backup"));
                viewSubtitleLabel.setText("System data backup and disaster recovery");
                break;
            case "/fxml/settings.fxml":
                viewTitleLabel.setText(I18n.t("nav.settings"));
                viewSubtitleLabel.setText("Configure application preferences and system logs");
                break;
        }
    }

    @FXML
    public void toggleLanguage() {
        String newLang = I18n.getCurrentLang().equals("en") ? "mr" : "en";
        I18n.load(newLang);
        refreshLabels();
        
        String msg = newLang.equals("en") ? "Switched to English" : "मराठी भाषा निवडली";
        Toast.success(contentArea, msg);
        
        // Reload current view to apply translations
        if (currentViewFxml != null) {
            load(currentViewFxml);
        }
    }

    @FXML public void showDashboard()    { currentViewFxml = "/fxml/dashboard.fxml"; load(currentViewFxml); activate(dashboardBtn); updateHeaderTitles(); }
    @FXML public void showAccounts()     { currentViewFxml = "/fxml/accounts.fxml";  load(currentViewFxml); activate(accountsBtn); updateHeaderTitles(); }
    @FXML public void showTransactions() { currentViewFxml = "/fxml/transactions.fxml"; load(currentViewFxml); activate(transactionsBtn); updateHeaderTitles(); }
    @FXML public void showInterest()     { currentViewFxml = "/fxml/interest.fxml";     load(currentViewFxml); activate(interestBtn); updateHeaderTitles(); }
    @FXML public void showDistributions(){ currentViewFxml = "/fxml/distributions.fxml"; load(currentViewFxml); activate(distributionBtn); updateHeaderTitles(); }
    @FXML public void showLoans()        { currentViewFxml = "/fxml/loans.fxml";       load(currentViewFxml); activate(loansBtn); updateHeaderTitles(); }
    @FXML public void showDailyOps()     { currentViewFxml = "/fxml/dailyops.fxml";   load(currentViewFxml); activate(dailyOpsBtn); updateHeaderTitles(); }
    @FXML public void showReports()      { currentViewFxml = "/fxml/reports.fxml";    load(currentViewFxml); activate(reportsBtn); updateHeaderTitles(); }
    @FXML public void showBackup()       { currentViewFxml = "/fxml/backup.fxml";     load(currentViewFxml); activate(backupBtn); updateHeaderTitles(); }
    @FXML public void showSettings()     { currentViewFxml = "/fxml/settings.fxml";   load(currentViewFxml); activate(settingsBtn); updateHeaderTitles(); }

    @FXML
    public void handleLogout() {
        var user = UserSession.getInstance().getCurrentUser();
        AuditLogger.log("LOGOUT", "USER", user.getId(), "Logout: " + user.getUsername(), user.getId());
        UserSession.getInstance().logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load(), 1000, 650);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            
            // Apply current theme to login screen
            if (!currentTheme.equals("light")) {
                scene.getRoot().getStyleClass().add("theme-" + currentTheme);
            }

            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setResizable(false);
            stage.setTitle("AutoBank");
            stage.setScene(scene);
        } catch (Exception e) {
            log.error("Failed to return to login screen", e);
        }
    }

    @FXML
    private void handleGlobalSearch() {
        String query = globalSearchField.getText();
        handleGlobalSearchWithQuery(query);
    }

    public void handleGlobalSearchWithQuery(String query) {
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            loader.setResources(I18n.getBundle());
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            log.error("Failed to load view: {}", fxml, e);
            showErrorView(fxml, e);
        }
    }

    private void showErrorView(String fxml, Exception e) {
        VBox box = new VBox(20);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.setStyle("-fx-background-color: white; -fx-padding: 40; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 20, 0, 0, 10);");
        box.setMaxSize(500, 350);
        
        Label icon = new Label("⚠️");
        icon.setStyle("-fx-font-size: 48px;");
        
        Label title = new Label("Module Load Error");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #e11d48;");
        
        Label sub = new Label("Could not load view: " + fxml + "\n\nError details:\n" + e.getMessage());
        sub.setStyle("-fx-text-alignment: center; -fx-text-fill: #64748b; -fx-font-size: 13px; -fx-font-family: 'Segoe UI';");
        sub.setWrapText(true);
        
        Button retryBtn = new Button("⟳  Retry Loading");
        retryBtn.getStyleClass().add("primary-button");
        retryBtn.setOnAction(evt -> load(fxml));
        
        box.getChildren().addAll(icon, title, sub, retryBtn);
        
        StackPane wrapper = new StackPane(box);
        wrapper.setStyle("-fx-background-color: #f8fafc;");
        contentArea.getChildren().setAll(wrapper);
    }

    private void activate(Button active) {
        currentActiveBtn = active;
        for (Button b : new Button[]{dashboardBtn, accountsBtn, transactionsBtn, interestBtn,
                                     distributionBtn, loansBtn, dailyOpsBtn, reportsBtn, 
                                     settingsBtn, backupBtn}) {
            if (b != null) b.getStyleClass().remove("active");
        }
        if (active != null) active.getStyleClass().add("active");
    }
}
