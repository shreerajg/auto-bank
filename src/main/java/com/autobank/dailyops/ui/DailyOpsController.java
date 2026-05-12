package com.autobank.dailyops.ui;

import com.autobank.dailyops.model.DailySession;
import com.autobank.dailyops.service.DailyOpsService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DailyOpsController {

    @FXML private Label todayDateLabel;
    @FXML private Label sessionStatusLabel;
    @FXML private Label openingBalLabel;
    @FXML private Label expectedCashLabel;
    @FXML private TextField openingBalField;
    @FXML private Button openDayBtn;
    @FXML private TextField actualCashField;
    @FXML private Button closeDayBtn;
    @FXML private Label statusLabel;

    @FXML private TableView<DailySession> historyTable;
    @FXML private TableColumn<DailySession, LocalDate>  colDate;
    @FXML private TableColumn<DailySession, BigDecimal> colOpening;
    @FXML private TableColumn<DailySession, BigDecimal> colExpected;
    @FXML private TableColumn<DailySession, BigDecimal> colActual;
    @FXML private TableColumn<DailySession, String>     colSessionStatus;

    private final DailyOpsService service = new DailyOpsService();
    private DailySession todaySession = null;

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colOpening.setCellValueFactory(new PropertyValueFactory<>("openingBalance"));
        colExpected.setCellValueFactory(new PropertyValueFactory<>("expectedCash"));
        colActual.setCellValueFactory(new PropertyValueFactory<>("actualCash"));
        colSessionStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        todayDateLabel.setText("Today: " + LocalDate.now());
        refresh();
    }

    private void refresh() {
        try {
            todaySession = service.getTodaySession();
            if (todaySession == null) {
                sessionStatusLabel.setText("Day NOT Opened");
                sessionStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                openingBalLabel.setText("—");
                expectedCashLabel.setText("—");
                openDayBtn.setDisable(false);
                closeDayBtn.setDisable(true);
            } else {
                String s = todaySession.getStatus();
                sessionStatusLabel.setText("Day " + s);
                sessionStatusLabel.setStyle("-fx-text-fill: " + ("OPEN".equals(s) ? "#27ae60" : "#888") + "; -fx-font-weight: bold;");
                openingBalLabel.setText("₹" + todaySession.getOpeningBalance());
                expectedCashLabel.setText("₹" + todaySession.getExpectedCash());
                openDayBtn.setDisable(true);
                closeDayBtn.setDisable(!"OPEN".equals(s));
            }
            historyTable.setItems(FXCollections.observableArrayList(service.getRecentSessions(30)));
        } catch (Exception e) {
            status("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleOpenDay() {
        String txt = openingBalField.getText().trim();
        if (txt.isEmpty()) { status("Enter opening cash balance"); return; }
        try {
            service.openDay(new BigDecimal(txt));
            status("✓ Day opened successfully");
            openingBalField.clear();
            refresh();
        } catch (Exception e) {
            status("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleCloseDay() {
        if (todaySession == null) { status("No open session today"); return; }
        String txt = actualCashField.getText().trim();
        if (txt.isEmpty()) { status("Enter actual cash in hand"); return; }
        try {
            service.closeDay(todaySession.getId(), new BigDecimal(txt));
            status("✓ Day closed. Check history below.");
            actualCashField.clear();
            refresh();
        } catch (Exception e) {
            status("Error: " + e.getMessage());
        }
    }

    private void status(String msg) { statusLabel.setText(msg); }
}
