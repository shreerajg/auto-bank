package com.autobank.reports.ui;

import com.autobank.config.DatabaseConfig;
import com.autobank.reports.service.ReportService;
import com.autobank.reports.service.ReportService.SummaryStats;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.MapValueFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class ReportController {

    @FXML private Label statAccounts;
    @FXML private Label statTotalBal;
    @FXML private Label statDeposits;
    @FXML private Label statWithdrawals;
    @FXML private Label statTxCount;
    @FXML private Label statActiveLoans;
    @FXML private Label statLoanOutstanding;
    @FXML private Label statusLabel;

    @FXML private BarChart<String, Number> volumeChart;
    @FXML private CategoryAxis chartXAxis;
    @FXML private NumberAxis chartYAxis;

    @FXML private TableView<Map<String, Object>> topAccountsTable;
    @FXML private TableColumn<Map<String, Object>, String>     colAccNum;
    @FXML private TableColumn<Map<String, Object>, String>     colAccName;
    @FXML private TableColumn<Map<String, Object>, BigDecimal> colAccBal;

    @FXML private ComboBox<Integer> dayRangeCombo;

    private final ReportService service = new ReportService();

    @FXML
    public void initialize() {
        colAccNum.setCellValueFactory(new MapValueFactory<>("accNum"));
        colAccName.setCellValueFactory(new MapValueFactory<>("name"));
        colAccBal.setCellValueFactory(new MapValueFactory<>("balance"));

        dayRangeCombo.setItems(FXCollections.observableArrayList(7, 14, 30, 60, 90));
        dayRangeCombo.setValue(30);
        dayRangeCombo.setOnAction(e -> loadChartData());

        chartXAxis.setLabel("Date");
        chartYAxis.setLabel("Amount (₹)");
        volumeChart.setTitle("Transaction Volume");

        loadAll();
    }

    @FXML private void handleRefresh() { loadAll(); }

    private void loadAll() {
        loadStats();
        loadChartData();
        loadTopAccounts();
    }

    private void loadStats() {
        try {
            SummaryStats s = service.getSummary();
            statAccounts.setText(String.valueOf(s.totalAccounts()));
            statTotalBal.setText("₹" + s.totalBalance());
            statDeposits.setText("₹" + s.totalDeposits());
            statWithdrawals.setText("₹" + s.totalWithdrawals());
            statTxCount.setText(String.valueOf(s.totalTransactions()));
            statActiveLoans.setText(String.valueOf(s.activeLoans()));
            statLoanOutstanding.setText("₹" + s.loanOutstanding());
        } catch (Exception e) {
            statusLabel.setText("Stats error: " + e.getMessage());
        }
    }

    private void loadChartData() {
        try {
            int days = dayRangeCombo.getValue();
            Map<String, BigDecimal[]> data = service.getDailyVolume(days);
            XYChart.Series<String, Number> deposits = new XYChart.Series<>();
            deposits.setName("Deposits");
            XYChart.Series<String, Number> withdrawals = new XYChart.Series<>();
            withdrawals.setName("Withdrawals");

            data.forEach((date, vals) -> {
                deposits.getData().add(new XYChart.Data<>(date, vals[0]));
                withdrawals.getData().add(new XYChart.Data<>(date, vals[1]));
            });
            volumeChart.getData().setAll(deposits, withdrawals);
        } catch (Exception e) {
            statusLabel.setText("Chart error: " + e.getMessage());
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
}
