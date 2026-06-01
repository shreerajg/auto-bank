package com.autobank.ui;

import com.autobank.config.DatabaseConfig;
import com.autobank.transaction.model.Transaction;
import com.autobank.transaction.service.TransactionService;
import com.autobank.util.I18n;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import javafx.scene.layout.VBox;
import com.autobank.util.Toast;

import com.autobank.ui.service.AnalyticsService;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;

public class DashboardController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DashboardController.class);

    @FXML private VBox mainContainer;
    @FXML private Label dashboardTitle;
    @FXML private Label dashboardSubtitle;
    @FXML private Label totalAccountsHeader;
    @FXML private Label totalBalanceHeader;
    @FXML private Label activeLoansHeader;
    @FXML private Label todayTxHeader;

    @FXML private Label totalAccountsLabel;
    @FXML private Label totalBalanceLabel;
    @FXML private Label activeLoansLabel;
    @FXML private Label todayTxLabel;
    @FXML private Label systemStatusLabel;

    @FXML private BarChart<String, Number> txChart;
    @FXML private PieChart loanPieChart;
    @FXML private LineChart<String, Number> cashFlowChart;
    
    @FXML private TableView<Transaction> recentTable;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, BigDecimal> colAmount;
    @FXML private TableColumn<Transaction, String> colDate;

    private final TransactionService txService = new TransactionService();
    private final AnalyticsService analyticsService = new AnalyticsService();

    @FXML
    public void initialize() {
        refreshLabels();
        refreshStats();
        loadChartData();
        loadAnalyticsCharts();
        loadRecentActivity();
    }

    private void refreshLabels() {
        dashboardTitle.setText(I18n.t("dashboard.title"));
        var user = com.autobank.auth.model.UserSession.getInstance().getCurrentUser();
        String name = (user != null) ? user.getUsername() : "User";
        dashboardSubtitle.setText(I18n.t("dashboard.subtitle") + ", " + name + "!");
        totalAccountsHeader.setText(I18n.t("dashboard.stats.accounts"));
        totalBalanceHeader.setText(I18n.t("dashboard.stats.balance"));
        activeLoansHeader.setText(I18n.t("dashboard.stats.loans"));
        todayTxHeader.setText(I18n.t("dashboard.stats.today"));
    }

    private void refreshStats() {
        run("SELECT COUNT(*) FROM accounts WHERE status = 'ACTIVE'",
            rs -> { if (rs.next()) totalAccountsLabel.setText(String.valueOf(rs.getInt(1))); });
        
        run("SELECT COALESCE(SUM(balance),0) FROM accounts WHERE status = 'ACTIVE'",
            rs -> { if (rs.next()) totalBalanceLabel.setText("₹ " + rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP)); });
        
        run("SELECT COUNT(*) FROM loans WHERE status = 'ACTIVE'",
            rs -> { if (rs.next()) activeLoansLabel.setText(String.valueOf(rs.getInt(1))); });
        
        run("SELECT COUNT(*) FROM transactions WHERE DATE(created_at) = CURDATE()",
            rs -> { if (rs.next()) todayTxLabel.setText(String.valueOf(rs.getInt(1))); });
    }

    private void loadChartData() {
        XYChart.Series<String, Number> depositSeries = new XYChart.Series<>();
        depositSeries.setName(I18n.t("common.deposits"));
        XYChart.Series<String, Number> withdrawSeries = new XYChart.Series<>();
        withdrawSeries.setName(I18n.t("common.withdrawals"));

        String sql = "SELECT DATE(created_at) as d, type, COUNT(*) as c " +
                     "FROM transactions " +
                     "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                     "GROUP BY d, type ORDER BY d";

        run(sql, rs -> {
            while (rs.next()) {
                java.sql.Date sqlDate = rs.getDate("d");
                if (sqlDate == null) continue;
                String date = sqlDate.toString();
                int count = rs.getInt("c");
                if (rs.getString("type").contains("DEPOSIT")) {
                    depositSeries.getData().add(new XYChart.Data<>(date, count));
                } else {
                    withdrawSeries.getData().add(new XYChart.Data<>(date, count));
                }
            }
        });

        txChart.getData().clear();
        txChart.getData().addAll(depositSeries, withdrawSeries);
    }

    private void loadAnalyticsCharts() {
        try {
            // 1. Loan Distribution
            var loanDist = analyticsService.getLoanDistribution();
            loanPieChart.getData().clear();
            loanDist.forEach((status, count) -> 
                loanPieChart.getData().add(new PieChart.Data(status, count)));

            // 2. Cash Flow
            var cashFlow = analyticsService.getMonthlyCashFlow(6);
            XYChart.Series<String, Number> depSeries = new XYChart.Series<>();
            depSeries.setName("Deposits");
            XYChart.Series<String, Number> wthSeries = new XYChart.Series<>();
            wthSeries.setName("Withdrawals");

            for (var data : cashFlow) {
                depSeries.getData().add(new XYChart.Data<>(data.month, data.deposits));
                wthSeries.getData().add(new XYChart.Data<>(data.month, data.withdrawals));
            }

            cashFlowChart.getData().clear();
            cashFlowChart.getData().addAll(depSeries, wthSeries);

        } catch (Exception e) {
            log.error("Failed to load analytics charts", e);
        }
    }

    private void loadRecentActivity() {
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        
        // Custom cell factory for date formatting
        colDate.setCellValueFactory(cellData -> {
            var date = cellData.getValue().getCreatedAt();
            return new javafx.beans.property.SimpleStringProperty(
                date != null ? date.format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "—"
            );
        });

        try {
            List<Transaction> list = txService.getRecent(8);
            recentTable.setItems(FXCollections.observableArrayList(list));
        } catch (Exception ignored) {}
    }

    @FXML
    private void handleNewAccount() {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showAccounts();
        }
    }

    @FXML
    private void handleDeposit() {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showTransactions();
        }
    }

    @FXML
    private void handleWithdraw() {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showTransactions();
        }
    }

    @FunctionalInterface
    interface RsHandler { void accept(ResultSet rs) throws Exception; }

    private void run(String sql, RsHandler handler) {
        try (Connection c = DatabaseConfig.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            handler.accept(rs);
        } catch (Exception e) {
            log.error("Database error in dashboard: {}", e.getMessage());
        }
    }
}
