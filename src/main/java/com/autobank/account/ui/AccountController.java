package com.autobank.account.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.math.BigDecimal;

public class AccountController {

    @FXML private TextField searchField;
    @FXML private TableView<Account> accountTable;
    @FXML private TableColumn<Account, String> colAccountNumber;
    @FXML private TableColumn<Account, String> colHolderName;
    @FXML private TableColumn<Account, String> colPhone;
    @FXML private TableColumn<Account, BigDecimal> colBalance;
    @FXML private TableColumn<Account, String> colStatus;
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField addressField;
    @FXML private Label statusLabel;

    private final AccountService accountService = new AccountService();

    @FXML
    public void initialize() {
        colAccountNumber.setCellValueFactory(new PropertyValueFactory<>("accountNumber"));
        colHolderName.setCellValueFactory(new PropertyValueFactory<>("holderName"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colBalance.setCellValueFactory(new PropertyValueFactory<>("balance"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        searchField.textProperty().addListener((obs, old, val) -> load(val));
        load("");
    }

    private void load(String query) {
        try {
            accountTable.setItems(FXCollections.observableArrayList(
                accountService.searchAccounts(query)));
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleCreateAccount() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { statusLabel.setText("Name is required"); return; }

        try {
            Account a = accountService.createAccount(name,
                phoneField.getText().trim(), addressField.getText().trim());
            statusLabel.setText("Created: " + a.getAccountNumber() + " — " + a.getHolderName());
            nameField.clear(); phoneField.clear(); addressField.clear();
            load(searchField.getText());
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }
}
