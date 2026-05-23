package com.autobank.account.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.util.DraftManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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
    
    @FXML private TabPane tabPane;
    @FXML private Tab createTab;
    @FXML private Button registerButton;
    @FXML private Label registerTitle;

    private final AccountService accountService = new AccountService();
    private final DraftManager draftManager = DraftManager.getInstance();
    private static final String FORM_ID = "ACCOUNT_NEW";
    private Account editingAccount;

    @FXML
    public void initialize() {
        // ... (existing col setup)
        colAccountNumber.setCellValueFactory(new PropertyValueFactory<>("accountNumber"));
        colHolderName.setCellValueFactory(new PropertyValueFactory<>("holderName"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colBalance.setCellValueFactory(new PropertyValueFactory<>("balance"));
        
        setupStatusColumn();

        searchField.textProperty().addListener((obs, old, val) -> load(val));
        
        accountTable.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) showDetails(val);
        });

        setupDraftListeners();
        loadDraft();
        load("");
    }

    private void setupDraftListeners() {
        nameField.textProperty().addListener((obs, old, val) -> {
            if (editingAccount == null) saveDraft();
        });
        phoneField.textProperty().addListener((obs, old, val) -> {
            if (editingAccount == null) saveDraft();
        });
        addressField.textProperty().addListener((obs, old, val) -> {
            if (editingAccount == null) saveDraft();
        });
    }

    // ... (saveDraft, loadDraft, setupStatusColumn, load, showDetails remain)

    @FXML
    private void handleEditDetails() {
        Account selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        editingAccount = selected;
        nameField.setText(selected.getHolderName());
        phoneField.setText(selected.getPhone());
        addressField.setText(selected.getAddress());

        registerTitle.setText("Edit Member: " + selected.getAccountNumber());
        registerButton.setText("💾  Update Account");
        
        tabPane.getSelectionModel().select(createTab);
    }

    @FXML
    private void handleCancelEdit() {
        resetForm();
    }

    private void resetForm() {
        editingAccount = null;
        nameField.clear();
        phoneField.clear();
        addressField.clear();
        registerTitle.setText("New Member Registration");
        registerButton.setText("✚  Register Member");
        loadDraft();
    }

    @FXML
    private void handleCreateAccount() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { statusLabel.setText("Name is required"); return; }

        try {
            if (editingAccount == null) {
                Account a = accountService.createAccount(name,
                    phoneField.getText().trim(), addressField.getText().trim());
                statusLabel.setText("Created: " + a.getAccountNumber());
            } else {
                editingAccount.setHolderName(name);
                editingAccount.setPhone(phoneField.getText().trim());
                editingAccount.setAddress(addressField.getText().trim());
                accountService.updateAccount(editingAccount);
                statusLabel.setText("Updated: " + editingAccount.getAccountNumber());
            }
            
            resetForm();
            draftManager.clearDraft(FORM_ID);
            load(searchField.getText());
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

}
