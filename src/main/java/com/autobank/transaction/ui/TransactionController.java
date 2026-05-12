package com.autobank.transaction.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.transaction.model.Transaction;
import com.autobank.transaction.service.TransactionService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.math.BigDecimal;

public class TransactionController {

    @FXML private TextField accountSearchField;
    @FXML private ComboBox<Account> accountCombo;
    @FXML private TextField amountField;
    @FXML private TextArea descriptionField;
    @FXML private Label statusLabel;
    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, Integer> colId;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, BigDecimal> colAmount;
    @FXML private TableColumn<Transaction, BigDecimal> colBalance;
    @FXML private TableColumn<Transaction, String> colDesc;
    @FXML private TableColumn<Transaction, String> colStatus;

    private final TransactionService txService = new TransactionService();
    private final AccountService accountService = new AccountService();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colBalance.setCellValueFactory(new PropertyValueFactory<>("balanceAfter"));
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        accountCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Account a) { return a == null ? "" : a.toString(); }
            @Override public Account fromString(String s) { return null; }
        });

        accountSearchField.textProperty().addListener((obs, old, val) -> {
            try {
                accountCombo.setItems(FXCollections.observableArrayList(
                    accountService.searchAccounts(val)));
            } catch (Exception ignored) {}
        });

        loadRecent();
    }

    @FXML private void handleDeposit()    { perform("DEPOSIT"); }
    @FXML private void handleWithdrawal() { perform("WITHDRAWAL"); }

    private void perform(String type) {
        Account account = accountCombo.getValue();
        String amtStr = amountField.getText().trim();
        if (account == null) { statusLabel.setText("Select an account"); return; }
        if (amtStr.isEmpty()) { statusLabel.setText("Enter amount"); return; }

        try {
            BigDecimal amt = new BigDecimal(amtStr);
            if (amt.compareTo(BigDecimal.ZERO) <= 0) { statusLabel.setText("Amount must be > 0"); return; }

            Transaction tx = "DEPOSIT".equals(type)
                ? txService.deposit(account.getId(), amt, descriptionField.getText())
                : txService.withdraw(account.getId(), amt, descriptionField.getText());

            statusLabel.setText(type + " of ₹" + amt + " successful — Ref #" + tx.getId());
            amountField.clear();
            descriptionField.clear();
            loadRecent();
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void loadRecent() {
        try {
            transactionTable.setItems(FXCollections.observableArrayList(txService.getRecent(100)));
        } catch (Exception e) {
            statusLabel.setText("Load error: " + e.getMessage());
        }
    }
}
