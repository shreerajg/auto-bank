package com.autobank.interest.ui;

import com.autobank.interest.service.InterestService;
import com.autobank.util.I18n;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InterestController {

    @FXML private ComboBox<Integer> yearCombo;
    @FXML private ComboBox<String> monthCombo;
    @FXML private RadioButton savingsRadio;
    @FXML private RadioButton loanRadio;
    @FXML private Button previewBtn;
    @FXML private Button executeBtn;
    @FXML private VBox previewArea;
    @FXML private Label countLabel;
    @FXML private Label totalAmountLabel;
    @FXML private VBox statusArea;

    private final InterestService interestService = new InterestService();
    private final String[] months = {
        "January", "February", "March", "April", "May", "June", 
        "July", "August", "September", "October", "November", "December"
    };

    @FXML
    public void initialize() {
        // Populate years (current and previous 2)
        int currentYear = LocalDate.now().getYear();
        List<Integer> years = new ArrayList<>();
        for (int i = 0; i < 3; i++) years.add(currentYear - i);
        yearCombo.setItems(FXCollections.observableArrayList(years));
        yearCombo.setValue(currentYear);

        // Populate months
        monthCombo.setItems(FXCollections.observableArrayList(months));
        monthCombo.setValue(months[LocalDate.now().getMonthValue() - 1]);
    }

    @FXML
    private void handlePreview() {
        try {
            int year = yearCombo.getValue();
            int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;
            
            InterestService.InterestPreview preview;
            if (savingsRadio.isSelected()) {
                preview = interestService.previewSavingsInterest(year, month);
            } else {
                preview = interestService.previewLoanInterest(year, month);
            }

            countLabel.setText(String.valueOf(preview.count));
            totalAmountLabel.setText("₹ " + preview.totalAmount.setScale(2, BigDecimal.ROUND_HALF_UP));
            previewArea.setVisible(true);
            executeBtn.setDisable(preview.count == 0);

        } catch (Exception e) {
            showAlert("Error", "Failed to generate preview: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleExecute() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Execution");
        confirm.setHeaderText("Are you sure you want to execute this interest batch?");
        confirm.setContentText("Month: " + monthCombo.getValue() + " " + yearCombo.getValue() + 
                               "\nType: " + (savingsRadio.isSelected() ? "Savings" : "Loan"));

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                int year = yearCombo.getValue();
                int month = monthCombo.getSelectionModel().getSelectedIndex() + 1;

                if (savingsRadio.isSelected()) {
                    interestService.processSavingsInterest(year, month);
                } else {
                    interestService.processLoanInterest(year, month);
                }

                showAlert("Success", "Interest batch processed successfully.", Alert.AlertType.INFORMATION);
                executeBtn.setDisable(true);
                previewArea.setVisible(false);

            } catch (Exception e) {
                showAlert("Error", "Execution failed: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
