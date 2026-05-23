package com.autobank.reports.ui;

import com.autobank.config.DatabaseConfig;
import com.autobank.reports.service.ReportService;
import com.autobank.reports.service.ReportService.SummaryStats;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class ReportController {

    // --- Stat card labels ---
    @FXML private Label statAccounts;
    @FXML private Label statTotalBal;
    @FXML private Label statDeposits;
    @FXML private Label statWithdrawals;
    @FXML private Label statTxCount;
    @FXML private Label statActiveLoans;
    @FXML private Label statLoanOutstanding;
    @FXML private Label statTodayTx;
    @FXML private Label statTodayVol;
    @FXML private Label statusLabel;

    // --- Charts ---
    @FXML private LineChart<String, Number>   trendChart;
    @FXML private CategoryAxis trendXAxis;
    @FXML private NumberAxis   trendYAxis;

    @FXML private PieChart splitPieChart;

    @FXML private BarChart<String, Number>   monthlyBarChart;
    @FXML private CategoryAxis monthXAxis;
    @FXML private NumberAxis   monthYAxis;

    @FXML private BarChart<String, Number>   loanStatusChart;
    @FXML private CategoryAxis loanXAxis;
    @FXML private NumberAxis   loanYAxis;

    // --- Top accounts table ---
    @FXML private TableView<Map<String, Object>> topAccountsTable;
    @FXML private TableColumn<Map<String, Object>, String>     colAccNum;
    @FXML private TableColumn<Map<String, Object>, String>     colAccName;
    @FXML private TableColumn<Map<String, Object>, BigDecimal> colAccBal;

    @FXML private ComboBox<Integer> dayRangeCombo;

    private final ReportService service = new ReportService();

    @FXML
    public void initialize() {
        colAccNum.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getOrDefault("accNum", "").toString()));
        colAccName.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getOrDefault("name", "").toString()));
        colAccBal.setCellValueFactory(cd -> new SimpleObjectProperty<>(
            (BigDecimal) cd.getValue().get("balance")));

        dayRangeCombo.setItems(FXCollections.observableArrayList(7, 14, 30, 60, 90));
        dayRangeCombo.setValue(30);
        dayRangeCombo.setOnAction(e -> loadTrendChart());

        trendXAxis.setLabel("Date");
        trendYAxis.setLabel("Amount (₹)");
        trendChart.setTitle("Daily Transaction Trend");
        trendChart.setCreateSymbols(false);
        trendChart.setAnimated(false);

        monthXAxis.setLabel("Month");
        monthYAxis.setLabel("Volume (₹)");
        monthlyBarChart.setTitle("Monthly Volume (Last 6 Months)");
        monthlyBarChart.setAnimated(false);

        loanXAxis.setLabel("Status");
        loanYAxis.setLabel("Count");
        loanStatusChart.setTitle("Loan Status");
        loanStatusChart.setAnimated(false);
        loanStatusChart.setLegendVisible(false);

        splitPieChart.setLabelsVisible(false);

        loadAll();
    }

    @FXML private void handleRefresh() { loadAll(); }

    @FXML
    private void handleExportBalances() {
        try {
            statusLabel.setText("Generating Member Balance Report...");
            String path = service.generateMemberBalanceReport();
            showSuccess("Report saved: " + path);
            openFile(path);
        } catch (Exception e) {
            showError("Export error: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportCashbook() {
        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            statusLabel.setText("Generating Today's Cashbook...");
            String path = service.generateDailyTransactionReport(today);
            showSuccess("Cashbook saved: " + path);
            openFile(path);
        } catch (Exception e) {
            showError("Export error: " + e.getMessage());
        }
    }

    private void openFile(String path) {
        try {
            java.io.File file = new java.io.File(path);
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            statusLabel.setText("Report generated but could not open automatically: " + path);
        }
    }

    private void showError(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: #dc2626;");
    }

    private void showSuccess(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: #059669;");
    }

    private void loadAll() {
        loadStats();
        loadTrendChart();
        loadPieChart();
        loadMonthlyBarChart();
        loadLoanStatusChart();
        loadTopAccounts();
    }

    private void loadStats() {
        try {
            SummaryStats s = service.getSummary();
            statAccounts.setText(String.valueOf(s.totalAccounts()));
            statTotalBal.setText("₹" + fmt(s.totalBalance()));
            statDeposits.setText("₹" + fmt(s.totalDeposits()));
            statWithdrawals.setText("₹" + fmt(s.totalWithdrawals()));
            statTxCount.setText(String.valueOf(s.totalTransactions()));
            statActiveLoans.setText(String.valueOf(s.activeLoans()));
            statLoanOutstanding.setText("₹" + fmt(s.loanOutstanding()));
            statTodayTx.setText(String.valueOf(s.todayTransactions()));
            statTodayVol.setText("₹" + fmt(s.todayVolume()));
        } catch (Exception e) {
            statusLabel.setText("Stats error: " + e.getMessage());
        }
    }

    private void loadTrendChart() {
        try {
            int days = dayRangeCombo.getValue();
            Map<String, BigDecimal[]> data = service.getDailyVolume(days);
            XYChart.Series<String, Number> deposits    = new XYChart.Series<>();
            XYChart.Series<String, Number> withdrawals = new XYChart.Series<>();
            deposits.setName("Deposits");
            withdrawals.setName("Withdrawals");
            data.forEach((date, vals) -> {
                deposits.getData().add(new XYChart.Data<>(date, vals[0]));
                withdrawals.getData().add(new XYChart.Data<>(date, vals[1]));
            });
            trendChart.getData().setAll(deposits, withdrawals);
        } catch (Exception e) {
            statusLabel.setText("Trend chart error: " + e.getMessage());
        }
    }

    private void loadPieChart() {
        try {
            SummaryStats s = service.getSummary();
            double dep  = s.totalDeposits().doubleValue();
            double with = s.totalWithdrawals().doubleValue();
            ObservableList<PieChart.Data> pie = FXCollections.observableArrayList(
                new PieChart.Data("Deposits", dep),
                new PieChart.Data("Withdrawals", with)
            );
            splitPieChart.setData(pie);
            splitPieChart.setTitle("Deposits ₹" + fmt(s.totalDeposits()) +
                                   "  |  Withdrawals ₹" + fmt(s.totalWithdrawals()));
        } catch (Exception e) {
            statusLabel.setText("Pie chart error: " + e.getMessage());
        }
    }

    private void loadMonthlyBarChart() {
        try {
            Map<String, BigDecimal> monthly = service.getMonthlyVolume(6);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Total Volume");
            monthly.forEach((month, vol) ->
                series.getData().add(new XYChart.Data<>(month, vol)));
            monthlyBarChart.getData().setAll(series);
        } catch (Exception e) {
            statusLabel.setText("Monthly chart error: " + e.getMessage());
        }
    }

    private void loadLoanStatusChart() {
        try {
            SummaryStats s = service.getSummary();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.getData().add(new XYChart.Data<>("Active",  s.activeLoans()));
            series.getData().add(new XYChart.Data<>("Closed",  s.closedLoans()));
            loanStatusChart.getData().setAll(series);
        } catch (Exception e) {
            statusLabel.setText("Loan chart error: " + e.getMessage());
        }
    }

    private void loadTopAccounts() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            ResultSet rs = service.getTopAccounts(conn, 10);
            ObservableList<Map<String, Object>> rows = FXCollections.observableArrayList();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("accNum",  rs.getString("account_number"));
                row.put("name",    rs.getString("holder_name"));
                row.put("balance", rs.getBigDecimal("balance"));
                rows.add(row);
            }
            topAccountsTable.setItems(rows);
        } catch (Exception e) {
            statusLabel.setText("Top accounts error: " + e.getMessage());
        }
    }

    private String fmt(BigDecimal val) {
        if (val == null) return "0";
        long v = val.longValue();
        if (v >= 10_00_000) return String.format("%.2fL", v / 1_00_000.0);
        if (v >= 1000)      return String.format("%.2fK", v / 1000.0);
        return val.toPlainString();
    }
}
