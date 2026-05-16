package com.autobank.account.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

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

    @FXML private VBox detailBox;
    @FXML private Label detailName;
    @FXML private Label detailAcc;
    @FXML private Label detailPhone;
    @FXML private Label detailBalance;
    @FXML private Label detailStatus;

    private final AccountService accountService = new AccountService();

    @FXML
    public void initialize() {
        colAccountNumber.setCellValueFactory(new PropertyValueFactory<>("accountNumber"));
        colHolderName.setCellValueFactory(new PropertyValueFactory<>("holderName"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colBalance.setCellValueFactory(new PropertyValueFactory<>("balance"));
        
        setupStatusColumn();

        searchField.textProperty().addListener((obs, old, val) -> load(val));
        
        accountTable.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) showDetails(val);
        });

        load("");
    }

    private void setupStatusColumn() {
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
                    } else if ("INACTIVE".equals(item)) {
                        setStyle("-fx-text-fill: #7f8c8d;");
                    } else {
                        setStyle("-fx-text-fill: #e67e22;");
                    }
                }
            }
        });
    }

    private void load(String query) {
        try {
            accountTable.setItems(FXCollections.observableArrayList(
                accountService.searchAccounts(query)));
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void showDetails(Account a) {
        if (detailBox != null) {
            detailBox.setVisible(true);
            detailName.setText(a.getHolderName());
            detailAcc.setText(a.getAccountNumber());
            detailPhone.setText(a.getPhone() != null ? a.getPhone() : "N/A");
            detailBalance.setText("₹ " + a.getBalance().toString());
            detailStatus.setText(a.getStatus());
        }
    }

    @FXML
    private void handleCreateAccount() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { statusLabel.setText("Name is required"); return; }

        try {
            Account a = accountService.createAccount(name,
                phoneField.getText().trim(), addressField.getText().trim());
            statusLabel.setText("Created: " + a.getAccountNumber());
            nameField.clear(); phoneField.clear(); addressField.clear();
            load(searchField.getText());
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }
}
