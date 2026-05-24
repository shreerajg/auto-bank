package com.autobank.transaction.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.transaction.model.Transaction;
import com.autobank.transaction.service.TransactionService;
import com.autobank.util.DraftManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class TransactionController {

    @FXML private TextField accountSearchField;
    @FXML private ComboBox<Account> accountCombo;
    @FXML private TextField amountField;
    @FXML private TextArea descriptionField;
    @FXML private Label statusLabel;
    
    @FXML private Label balanceValueLabel;
    @FXML private Label selectedAccountLabel;

    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, Integer> colId;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, BigDecimal> colAmount;
    @FXML private TableColumn<Transaction, BigDecimal> colBalance;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colStatus;

    private final TransactionService txService = new TransactionService();
    private final AccountService accountService = new AccountService();
    private final DraftManager draftManager = DraftManager.getInstance();
    private static final String FORM_ID = "TRANSACTION_NEW";

    @FXML
    public void initialize() {
        setupTable();
        setupAccountCombo();

        accountSearchField.textProperty().addListener((obs, old, val) -> {
            try {
                accountCombo.setItems(FXCollections.observableArrayList(
                    accountService.searchAccounts(val)));
                if (!accountCombo.getItems().isEmpty()) {
                    accountCombo.show();
                }
            } catch (Exception ignored) {}
        });

        accountCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) {
                balanceValueLabel.setText("₹ " + val.getBalance());
                selectedAccountLabel.setText(val.getHolderName());
                amountField.requestFocus();
            } else {
                balanceValueLabel.setText("₹ 0.00");
                selectedAccountLabel.setText("No account selected");
            }
            saveDraft();
        });

        setupDraftListeners();
        loadDraft();
        loadRecent();
    }

    private void setupDraftListeners() {
        amountField.textProperty().addListener((obs, old, val) -> saveDraft());
        descriptionField.textProperty().addListener((obs, old, val) -> saveDraft());
    }

    private void saveDraft() {
        Map<String, String> data = new HashMap<>();
        data.put("amount", amountField.getText());
        data.put("description", descriptionField.getText());
        Account selected = accountCombo.getValue();
        if (selected != null) {
            data.put("accountId", String.valueOf(selected.getId()));
            data.put("accountNum", selected.getAccountNumber());
        }
        draftManager.saveDraft(FORM_ID, data);
    }

    private void loadDraft() {
        Map<String, String> data = draftManager.getDraft(FORM_ID);
        if (!data.isEmpty()) {
            amountField.setText(data.getOrDefault("amount", ""));
            descriptionField.setText(data.getOrDefault("description", ""));
            String accNum = data.get("accountNum");
            if (accNum != null) {
                accountSearchField.setText(accNum);
                // The listener on accountSearchField will populate accountCombo
                // We'll trust the user to re-select or we could try to auto-select
            }
            if (statusLabel != null) showSuccess("Unsaved transaction draft loaded");
        }
    }

    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colBalance.setCellValueFactory(new PropertyValueFactory<>("balanceAfter"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("ACTIVE".equals(item)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else if ("REVERSED".equals(item)) {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-style: italic;");
                    } else {
                        setStyle("-fx-text-fill: #7f8c8d;");
                    }
                }
            }
        });
        
        setupContextMenu();
        
        colDate.setCellValueFactory(cellData -> {
            var date = cellData.getValue().getCreatedAt();
            return new javafx.beans.property.SimpleStringProperty(
                date != null ? date.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) : "—"
            );
        });
    }

    private void setupAccountCombo() {
        accountCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Account a) { return a == null ? "" : a.getAccountNumber() + " - " + a.getHolderName(); }
            @Override public Account fromString(String s) { return null; }
        });
    }

    @FXML private void handleDeposit()    { perform("DEPOSIT"); }
    @FXML private void handleWithdrawal() { perform("WITHDRAWAL"); }

    private void perform(String type) {
        Account account = accountCombo.getValue();
        String amtStr = amountField.getText().trim();
        if (account == null) { showError("Select an account first"); return; }
        if (amtStr.isEmpty()) { showError("Enter an amount"); return; }

        try {
            BigDecimal amt = new BigDecimal(amtStr);
            if (amt.compareTo(BigDecimal.ZERO) <= 0) { showError("Amount must be positive"); return; }

            Transaction tx = "DEPOSIT".equals(type)
                ? txService.deposit(account.getId(), amt, descriptionField.getText())
                : txService.withdraw(account.getId(), amt, descriptionField.getText());

            showSuccess(type + " successful: ₹" + amt);
            clearForm();
            draftManager.clearDraft(FORM_ID);
            loadRecent();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void clearForm() {
        amountField.clear();
        descriptionField.clear();
        accountSearchField.clear();
        accountCombo.getSelectionModel().clearSelection();
    }

    private void showError(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: #dc2626;");
    }

    private void showSuccess(String msg) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: #059669;");
    }

    private void loadRecent() {
        try {
            transactionTable.setItems(FXCollections.observableArrayList(txService.getRecent(100)));
        } catch (Exception e) {
            showError("Load error: " + e.getMessage());
        }
    }
}
