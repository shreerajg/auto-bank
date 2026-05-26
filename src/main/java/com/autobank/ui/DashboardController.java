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

public class DashboardController {

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
    @FXML private TableView<Transaction> recentTable;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, BigDecimal> colAmount;
    @FXML private TableColumn<Transaction, String> colDate;

    private final TransactionService txService = new TransactionService();

    @FXML
    public void initialize() {
        refreshLabels();
        refreshStats();
        loadChartData();
        loadRecentActivity();
    }

    private void refreshLabels() {
        dashboardTitle.setText(I18n.t("dashboard.title"));
        String user = com.autobank.auth.model.UserSession.getInstance().getCurrentUser().getUsername();
        dashboardSubtitle.setText(I18n.t("dashboard.subtitle") + ", " + user + "!");
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
        
        run("SELECT COUNT(*) FROM transactions WHERE created_at::date = CURRENT_DATE",
            rs -> { if (rs.next()) todayTxLabel.setText(String.valueOf(rs.getInt(1))); });
    }

    private void loadChartData() {
        XYChart.Series<String, Number> depositSeries = new XYChart.Series<>();
        depositSeries.setName(I18n.getCurrentLang().equals("en") ? "Deposits" : "ठेवी");
        XYChart.Series<String, Number> withdrawSeries = new XYChart.Series<>();
        withdrawSeries.setName(I18n.getCurrentLang().equals("en") ? "Withdrawals" : "काढणे");

        String sql = "SELECT created_at::date as d, type, COUNT(*) as c " +
                     "FROM transactions " +
                     "WHERE created_at >= CURRENT_DATE - INTERVAL '7 days' " +
                     "GROUP BY d, type ORDER BY d";

        run(sql, rs -> {
            while (rs.next()) {
                String date = rs.getDate("d").toString();
                int count = rs.getInt("c");
                if ("DEPOSIT".equals(rs.getString("type"))) {
                    depositSeries.getData().add(new XYChart.Data<>(date, count));
                } else {
                    withdrawSeries.getData().add(new XYChart.Data<>(date, count));
                }
            }
        });

        txChart.getData().addAll(depositSeries, withdrawSeries);
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

    @FunctionalInterface
    interface RsHandler { void accept(ResultSet rs) throws Exception; }

    private void run(String sql, RsHandler handler) {
        try (Connection c = DatabaseConfig.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            handler.accept(rs);
        } catch (Exception ignored) {}
    }
}
