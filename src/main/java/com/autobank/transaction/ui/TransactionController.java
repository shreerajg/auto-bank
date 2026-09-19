package com.autobank.transaction.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.transaction.model.Transaction;
import com.autobank.transaction.service.TransactionService;
import com.autobank.ui.MainController;
import com.autobank.util.DraftManager;
import com.autobank.util.I18n;
import com.autobank.util.Toast;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class TransactionController {

    @FXML private TextField accountSearchField;
    @FXML private ComboBox<Account> accountCombo;
    @FXML private TextField amountField;
    @FXML private TextArea descriptionField;
    @FXML private Label statusLabel;
    
    @FXML private Label balanceValueLabel;
    @FXML private Label selectedAccountLabel;

    // History Tab
    @FXML private TextField searchField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;

    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, Integer> colId;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, BigDecimal> colAmount;
    @FXML private TableColumn<Transaction, BigDecimal> colBalance;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colStatus;

    // Cashbook Tab
    @FXML private DatePicker cashbookDatePicker;
    @FXML private Label cbOpenBal;
    @FXML private Label cbTotalIn;
    @FXML private Label cbTotalOut;
    @FXML private Label cbCloseBal;
    @FXML private TableView<Transaction> cashbookTable;
    @FXML private TableColumn<Transaction, Integer> colCbId;
    @FXML private TableColumn<Transaction, String> colCbType;
    @FXML private TableColumn<Transaction, BigDecimal> colCbAmount;
    @FXML private TableColumn<Transaction, String> colCbDesc;

    private final TransactionService txService = new TransactionService();
    private final AccountService accountService = new AccountService();
    private final DraftManager draftManager = DraftManager.getInstance();
    private static final String FORM_ID = "TRANSACTION_NEW";

    @FXML
    public void initialize() {
        setupTable();
        setupCashbookTable();
        setupAccountCombo();

        if (cashbookDatePicker != null) {
            cashbookDatePicker.setValue(LocalDate.now());
        }

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

        if (searchField != null) {
            searchField.textProperty().addListener((obs, old, val) -> {
                if (val.isEmpty()) handleSearch();
            });
        }

        setupDraftListeners();
        loadDraft();
        loadRecent();
        
        if (cashbookDatePicker != null) {
            Platform.runLater(this::loadCashbook);
        }

        Platform.runLater(accountSearchField::requestFocus);
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
            }
            MainController.showToast("Unsaved transaction draft loaded", Toast.Type.INFO);
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

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem reverseItem = new MenuItem(I18n.t("tx.menu.reverse"));
        reverseItem.setOnAction(e -> handleReverseTransaction());
        menu.getItems().add(reverseItem);

        transactionTable.setRowFactory(tv -> {
            TableRow<Transaction> row = new TableRow<>();
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(menu));
            return row;
        });
    }

    private void handleReverseTransaction() {
        Transaction selected = transactionTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        if (!"ACTIVE".equals(selected.getStatus())) {
            MainController.showToast(I18n.t("tx.msg.reverse_only_active"), Toast.Type.ERROR);
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(I18n.t("tx.msg.reverse_title"));
        dialog.setHeaderText(I18n.t("tx.msg.reverse_header") + selected.getId());
        dialog.setContentText(I18n.t("tx.msg.reverse_reason"));
        
        dialog.showAndWait().ifPresent(reason -> {
            if (reason.trim().isEmpty()) {
                MainController.showToast(I18n.t("tx.msg.reverse_reason_req"), Toast.Type.ERROR);
                return;
            }
            try {
                txService.reverseTransaction(selected.getId(), reason);
                MainController.showToast(I18n.t("tx.msg.reversed") + " #" + selected.getId(), Toast.Type.SUCCESS);
                loadRecent();
            } catch (Exception e) {
                MainController.showToast(e.getMessage(), Toast.Type.ERROR);
            }
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
        if (account == null) { MainController.showToast("Select an account first", Toast.Type.ERROR); return; }
        if (amtStr.isEmpty()) { MainController.showToast("Enter an amount", Toast.Type.ERROR); return; }

        try {
            BigDecimal amt = new BigDecimal(amtStr);
            if (amt.compareTo(BigDecimal.ZERO) <= 0) { MainController.showToast("Amount must be positive", Toast.Type.ERROR); return; }

            Transaction tx = "DEPOSIT".equals(type)
                ? txService.deposit(account.getId(), amt, descriptionField.getText())
                : txService.withdraw(account.getId(), amt, descriptionField.getText());

            MainController.showToast(type + " successful: ₹" + amt, Toast.Type.SUCCESS);
            clearForm();
            draftManager.clearDraft(FORM_ID);
            loadRecent();
        } catch (Exception e) {
            MainController.showToast(e.getMessage(), Toast.Type.ERROR);
        }
    }

    @FXML
    private void handleClearForm() {
        clearForm();
        draftManager.clearDraft(FORM_ID);
        MainController.showToast("Form and draft cleared", Toast.Type.INFO);
    }

    private void clearForm() {
        amountField.clear();
        descriptionField.clear();
        accountSearchField.clear();
        accountCombo.getSelectionModel().clearSelection();
    }

    private void loadRecent() {
        try {
            transactionTable.setItems(FXCollections.observableArrayList(txService.getRecent(100)));
        } catch (Exception e) {
            MainController.showToast("Load error: " + e.getMessage(), Toast.Type.ERROR);
        }
    }

    @FXML
    private void handleSearch() {
        try {
            String query = searchField != null ? searchField.getText() : null;
            LocalDate start = startDatePicker != null ? startDatePicker.getValue() : null;
            LocalDate end = endDatePicker != null ? endDatePicker.getValue() : null;
            transactionTable.setItems(FXCollections.observableArrayList(txService.searchTransactions(query, start, end)));
        } catch (Exception e) {
            MainController.showToast("Search error: " + e.getMessage(), Toast.Type.ERROR);
        }
    }

    @FXML
    private void handleResetFilter() {
        if (searchField != null) searchField.clear();
        if (startDatePicker != null) startDatePicker.setValue(null);
        if (endDatePicker != null) endDatePicker.setValue(null);
        loadRecent();
    }

    private void setupCashbookTable() {
        if (colCbId == null) return;
        colCbId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colCbType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colCbAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colCbDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
    }

    @FXML
    private void loadCashbook() {
        if (cashbookDatePicker == null || cashbookTable == null) return;
        LocalDate date = cashbookDatePicker.getValue();
        if (date == null) {
            date = LocalDate.now();
            cashbookDatePicker.setValue(date);
        }
        
        try {
            TransactionService.CashbookSummary summary = txService.getCashbook(date);
            cbOpenBal.setText("₹ " + String.format("%.2f", summary.openingBalance));
            cbTotalIn.setText("₹ " + String.format("%.2f", summary.totalIn));
            cbTotalOut.setText("₹ " + String.format("%.2f", summary.totalOut));
            cbCloseBal.setText("₹ " + String.format("%.2f", summary.closingBalance));
            cashbookTable.setItems(FXCollections.observableArrayList(summary.transactions));
        } catch (Exception e) {
            MainController.showToast("Cashbook error: " + e.getMessage(), Toast.Type.ERROR);
        }
    }
}
