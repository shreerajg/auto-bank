package com.autobank.distribution.ui;

import com.autobank.distribution.model.DistributionRecord;
import com.autobank.distribution.model.PaymentDistribution;
import com.autobank.distribution.service.DistributionService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;

public class DistributionController {

    private static final Logger log = LoggerFactory.getLogger(DistributionController.class);

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
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
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
                log.error("File import failed", e);
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

    @FXML
    private void handleExport() {
        if (currentDist == null) return;
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        chooser.setInitialFileName("Distribution_" + currentDist.getId() + ".xlsx");
        File file = chooser.showSaveDialog(statusLabel.getScene().getWindow());
        if (file != null) {
            try {
                // Use python/excel_handler.py to export to excel
                java.util.List<DistributionRecord> records = service.getRecords(currentDist.getId());
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("headers", new String[]{"Name", "Account Number", "Amount", "Status", "Error Message"});
                java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
                for (DistributionRecord r : records) {
                    rows.add(java.util.List.of(
                        r.getHolderName() != null ? r.getHolderName() : "",
                        r.getAccountNumber() != null ? r.getAccountNumber() : "",
                        r.getAmount() != null ? r.getAmount().toString() : "",
                        r.getStatus() != null ? r.getStatus() : "",
                        r.getErrorMessage() != null ? r.getErrorMessage() : ""
                    ));
                }
                data.put("rows", rows);

                // Create temp JSON file
                File tempJson = File.createTempFile("export_data_", ".json");
                try (java.io.FileWriter writer = new java.io.FileWriter(tempJson)) {
                    new com.google.gson.Gson().toJson(data, writer);
                }

                // Temporary python script to call excel_handler.py export function
                File pyTemp = File.createTempFile("run_excel_export", ".py");
                try (java.io.FileWriter pyWriter = new java.io.FileWriter(pyTemp)) {
                    pyWriter.write("import json, sys\n" +
                                   "import python.excel_handler as eh\n" +
                                   "with open(sys.argv[1], 'r', encoding='utf-8') as f:\n" +
                                   "    data = json.load(f)\n" +
                                   "res = eh.export_to_excel(data, sys.argv[2])\n" +
                                   "print(json.dumps(res))\n");
                }

                ProcessBuilder pb = new ProcessBuilder("python", pyTemp.getAbsolutePath(), 
                        tempJson.getAbsolutePath(), file.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                if (p.waitFor() != 0) throw new Exception("Export failed");
                
                tempJson.delete();
                pyTemp.delete();

                statusLabel.setText("Exported successfully to " + file.getName());
                statusLabel.setStyle("-fx-text-fill: #059669;");
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(file);
                }
            } catch (Exception e) {
                statusLabel.setText("Export error: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #dc2626;");
                log.error("Export failed", e);
            }
        }
    }

    private void loadRecords() {
        try {
            recordTable.setItems(FXCollections.observableArrayList(service.getRecords(currentDist.getId())));
        } catch (Exception e) {
            statusLabel.setText("Load error: " + e.getMessage());
        }
    }
}
