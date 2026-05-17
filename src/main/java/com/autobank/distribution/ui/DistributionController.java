package com.autobank.distribution.ui;

import com.autobank.distribution.model.DistributionRecord;
import com.autobank.distribution.model.PaymentDistribution;
import com.autobank.distribution.service.DistributionService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;

public class DistributionController {

    @FXML private Label statusLabel;
    @FXML private Button processBtn;
    @FXML private TableView<DistributionRecord> recordTable;
    @FXML private TableColumn<DistributionRecord, String> colName;
    @FXML private TableColumn<DistributionRecord, String> colAcc;
    @FXML private TableColumn<DistributionRecord, BigDecimal> colAmount;
    @FXML private TableColumn<DistributionRecord, String> colStatus;
    @FXML private TableColumn<DistributionRecord, String> colError;

    private final DistributionService service = new DistributionService();
    private PaymentDistribution currentDist;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyFactory<>("holderName"));
        colAcc.setCellValueFactory(new PropertyFactory<>("accountNumber"));
        colAmount.setCellValueFactory(new PropertyFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyFactory<>("status"));
        colError.setCellValueFactory(new PropertyFactory<>("errorMessage"));

        loadPendingDraft();
    }

    private void loadPendingDraft() {
        try {
            currentDist = service.getLatestPendingDistribution();
            if (currentDist != null) {
                loadRecords();
                statusLabel.setText(String.format("Unprocessed Draft Found: %d records, %d matched.", 
                    currentDist.getTotalRecords(), currentDist.getMatchedRecords()));
                processBtn.setDisable(currentDist.getMatchedRecords() == 0);
                statusLabel.setStyle("-fx-text-fill: #d97706;");
            }
        } catch (Exception ignored) {}
    }

    // Helper class since PropertyValueFactory is picky about naming or we need full getters
    public static class PropertyFactory<S, T> extends PropertyValueFactory<S, T> {
        public PropertyFactory(String property) { super(property); }
    }

    @FXML
    private void handleImport() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        File file = chooser.showOpenDialog(statusLabel.getScene().getWindow());
        if (file != null) {
            try {
                statusLabel.setText("Importing " + file.getName() + "...");
                statusLabel.setStyle("-fx-text-fill: #2563eb;");
                currentDist = service.parseFile(file.getAbsolutePath());
                loadRecords();
                statusLabel.setText(String.format("Loaded: %d records, %d matched. Total: ₹%.2f", 
                    currentDist.getTotalRecords(), currentDist.getMatchedRecords(), currentDist.getTotalAmount()));
                processBtn.setDisable(currentDist.getMatchedRecords() == 0);
            } catch (Exception e) {
                statusLabel.setText("Import error: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc2626;");
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleProcess() {
        if (currentDist == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, 
            "Are you sure you want to credit " + currentDist.getMatchedRecords() + " accounts?", 
            ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.YES) {
                try {
                    processBtn.setDisable(true);
                    service.processDistribution(currentDist.getId());
                    loadRecords();
                    statusLabel.setText("Distribution completed for #" + currentDist.getId());
                    statusLabel.setStyle("-fx-text-fill: #059669;");
                } catch (Exception e) {
                    statusLabel.setText("Process error: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #dc2626;");
                }
            }
        });
    }

    private void loadRecords() {
        try {
            recordTable.setItems(FXCollections.observableArrayList(service.getRecords(currentDist.getId())));
        } catch (Exception e) {
            statusLabel.setText("Load error: " + e.getMessage());
        }
    }
}
