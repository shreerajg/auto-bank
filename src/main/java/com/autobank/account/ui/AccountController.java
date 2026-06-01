package com.autobank.account.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.ui.MainController;
import com.autobank.util.DraftManager;
import com.autobank.util.I18n;
import com.autobank.util.Toast;
import javafx.application.Platform;
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
        if (accountTable == null) return;

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

        if (searchField != null) {
            Platform.runLater(searchField::requestFocus);
        }
    }

    @FXML
    public void handleRefresh() {
        load(searchField != null ? searchField.getText() : "");
        MainController.showToast(I18n.t("common.refresh"), Toast.Type.INFO);
    }

    private void setupDraftListeners() {
        if (nameField == null) return;
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
        if (nameField == null) return;
        Map<String, String> data = new HashMap<>();
        data.put("name", nameField.getText());
        data.put("phone", phoneField.getText());
        data.put("address", addressField.getText());
        draftManager.saveDraft(FORM_ID, data);
    }

    private void loadDraft() {
        if (nameField == null) return;
        Map<String, String> data = draftManager.getDraft(FORM_ID);
        if (!data.isEmpty()) {
            nameField.setText(data.getOrDefault("name", ""));
            phoneField.setText(data.getOrDefault("phone", ""));
            addressField.setText(data.getOrDefault("address", ""));
            MainController.showToast(I18n.t("msg.draft_loaded"), Toast.Type.INFO);
        }
    }

    private void setupStatusColumn() {
        if (colStatus == null) return;
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
                        setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                    } else if ("INACTIVE".equals(item)) {
                        setStyle("-fx-text-fill: #64748b;");
                    } else {
                        setStyle("-fx-text-fill: #d97706;");
                    }
                }
            }
        });
    }

    private void load(String query) {
        if (accountTable == null) return;
        try {
            accountTable.setItems(FXCollections.observableArrayList(
                accountService.searchAccounts(query)));
            if (statusLabel != null) {
                statusLabel.setText(String.format(I18n.t("accounts.count_msg"), accountTable.getItems().size()));
            }
        } catch (Exception e) {
            MainController.showToast("Search Error: " + e.getMessage(), Toast.Type.ERROR);
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
            
            // Apply status style to detail view
            if ("ACTIVE".equals(a.getStatus())) {
                detailStatus.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
            } else {
                detailStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            }
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

        if (registerTitle != null) registerTitle.setText(I18n.t("accounts.button.edit") + ": " + selected.getAccountNumber());
        if (registerButton != null) registerButton.setText(I18n.t("accounts.button.update"));
        
        if (tabPane != null && createTab != null) {
            tabPane.getSelectionModel().select(createTab);
        }
    }

    @FXML
    private void handleDeactivateAccount() {
        Account selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.t("accounts.button.deactivate"));
        alert.setHeaderText(I18n.t("msg.deactivate_confirm"));
        alert.setContentText(I18n.t("accounts.table.acc_no") + ": " + selected.getAccountNumber() + "\n" +
                            I18n.t("accounts.table.name") + ": " + selected.getHolderName());

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                accountService.deactivateAccount(selected.getId(), "Manual deactivation by operator");
                MainController.showToast(I18n.t("common.success") + ": " + selected.getAccountNumber(), Toast.Type.SUCCESS);
                load(searchField != null ? searchField.getText() : "");
                if (detailBox != null) detailBox.setVisible(false);
            } catch (Exception e) {
                MainController.showToast(I18n.t("common.error") + ": " + e.getMessage(), Toast.Type.ERROR);
            }
        }
    }


    @FXML
    private void handleCreateAccount() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { 
            MainController.showToast("Name is required", Toast.Type.ERROR); 
            return; 
        }

        try {
            if (editingAccount == null) {
                Account a = accountService.createAccount(name,
                    phoneField.getText().trim(), addressField.getText().trim());
                MainController.showToast("Account Created: " + a.getAccountNumber(), Toast.Type.SUCCESS);
            } else {
                editingAccount.setHolderName(name);
                editingAccount.setPhone(phoneField.getText().trim());
                editingAccount.setAddress(addressField.getText().trim());
                accountService.updateAccount(editingAccount);
                MainController.showToast("Account Updated: " + editingAccount.getAccountNumber(), Toast.Type.SUCCESS);
            }
            
            resetForm();
            draftManager.clearDraft(FORM_ID);
            load(searchField.getText());
        } catch (Exception e) {
            MainController.showToast("Error: " + e.getMessage(), Toast.Type.ERROR);
        }
    }

    @FXML
    private void handleCancelEdit() {
        resetForm();
        if (tabPane != null) {
            tabPane.getSelectionModel().select(0);
        }
    }

    @FXML
    private void handleClearDraft() {
        nameField.clear();
        phoneField.clear();
        addressField.clear();
        draftManager.clearDraft(FORM_ID);
        MainController.showToast("Draft cleared", Toast.Type.INFO);
    }

    private void resetForm() {
        editingAccount = null;
        nameField.clear();
        phoneField.clear();
        addressField.clear();
        if (registerTitle != null) registerTitle.setText(I18n.t("accounts.label.register_title"));
        if (registerButton != null) registerButton.setText(I18n.t("accounts.button.register"));
        loadDraft();
    }
}
