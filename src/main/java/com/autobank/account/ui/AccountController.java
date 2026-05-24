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
    @FXML private TextArea addressField;
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

        String globalQuery = MainController.getPendingSearchQuery();
        if (globalQuery != null) {
            searchField.setText(globalQuery);
            load(globalQuery);
        } else {
            load("");
        }
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

    private void saveDraft() {
        Map<String, String> data = new HashMap<>();
        data.put("name", nameField.getText());
        data.put("phone", phoneField.getText());
        data.put("address", addressField.getText());
        draftManager.saveDraft(FORM_ID, data);
    }

    private void loadDraft() {
        Map<String, String> data = draftManager.getDraft(FORM_ID);
        if (!data.isEmpty()) {
            nameField.setText(data.getOrDefault("name", ""));
            phoneField.setText(data.getOrDefault("phone", ""));
            addressField.setText(data.getOrDefault("address", ""));
            if (statusLabel != null) statusLabel.setText("Draft loaded automatically");
        }
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
            if (statusLabel != null) statusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void showDetails(Account a) {
        if (detailBox != null) {
            detailBox.setVisible(true);
            detailName.setText(a.getHolderName());
            detailAcc.setText(a.getAccountNumber());
            detailPhone.setText(a.getPhone() != null && !a.getPhone().isEmpty() ? a.getPhone() : "N/A");
            detailBalance.setText("₹ " + (a.getBalance() != null ? a.getBalance().toString() : "0.00"));
            detailStatus.setText(a.getStatus());
        }
    }

    @FXML
    private void handleEditDetails() {
        Account selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        editingAccount = selected;
        nameField.setText(selected.getHolderName());
        phoneField.setText(selected.getPhone() != null ? selected.getPhone() : "");
        addressField.setText(selected.getAddress() != null ? selected.getAddress() : "");

        if (registerTitle != null) registerTitle.setText("Edit Member: " + selected.getAccountNumber());
        if (registerButton != null) registerButton.setText("💾  Update Account");
        
        if (tabPane != null && createTab != null) {
            tabPane.getSelectionModel().select(createTab);
        }
    }

    @FXML
    private void handleDeactivateAccount() {
        Account selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Deactivate Account");
        alert.setHeaderText("Are you sure you want to deactivate this account?");
        alert.setContentText("Account: " + selected.getAccountNumber() + "\nHolder: " + selected.getHolderName());

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                accountService.deactivateAccount(selected.getId(), "Manual deactivation by operator");
                statusLabel.setText("Deactivated: " + selected.getAccountNumber());
                load(searchField.getText());
                detailBox.setVisible(false);
            } catch (Exception e) {
                statusLabel.setText("Error: " + e.getMessage());
            }
        }
    }


    @FXML
    private void handleCreateAccount() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { 
            if (statusLabel != null) statusLabel.setText("Name is required"); 
            return; 
        }

        try {
            if (editingAccount == null) {
                Account a = accountService.createAccount(name,
                    phoneField.getText().trim(), addressField.getText().trim());
                if (statusLabel != null) statusLabel.setText("Created: " + a.getAccountNumber());
            } else {
                editingAccount.setHolderName(name);
                editingAccount.setPhone(phoneField.getText().trim());
                editingAccount.setAddress(addressField.getText().trim());
                accountService.updateAccount(editingAccount);
                if (statusLabel != null) statusLabel.setText("Updated: " + editingAccount.getAccountNumber());
            }
            
            resetForm();
            draftManager.clearDraft(FORM_ID);
            load(searchField.getText());
        } catch (Exception e) {
            if (statusLabel != null) statusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void resetForm() {
        editingAccount = null;
        nameField.clear();
        phoneField.clear();
        addressField.clear();
        if (registerTitle != null) registerTitle.setText("New Member Registration");
        if (registerButton != null) registerButton.setText("✚  Register Member");
        loadDraft();
    }
}
