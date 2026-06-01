package com.autobank.loan.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.loan.model.Loan;
import com.autobank.loan.service.LoanService;
import com.autobank.util.DraftManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class LoanController {

    @FXML private TextField loanSearchField;
    @FXML private ComboBox<Account> loanAccountCombo;
    @FXML private TextField loanAmountField;
    @FXML private TextField interestRateField;
    @FXML private TextField installmentField;
    @FXML private TextField dueDateField;
    @FXML private Label statusLabel;
    @FXML private TableView<Loan> loanTable;
    @FXML private TableColumn<Loan, Integer> colId;
    @FXML private TableColumn<Loan, String>  colHolder;
    @FXML private TableColumn<Loan, BigDecimal> colAmount;
    @FXML private TableColumn<Loan, BigDecimal> colOutstanding;
    @FXML private TableColumn<Loan, BigDecimal> colInterest;
    @FXML private TableColumn<Loan, String>  colStatus;
    @FXML private TextField payLoanIdField;
    @FXML private TextField payAmountField;
    @FXML private ComboBox<String> filterCombo;

    private final LoanService loanService   = new LoanService();
    private final AccountService accService = new AccountService();
    private final DraftManager draftManager = DraftManager.getInstance();
    private static final String FORM_ID = "LOAN_DISBURSE";

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colHolder.setCellValueFactory(new PropertyValueFactory<>("holderName"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colOutstanding.setCellValueFactory(new PropertyValueFactory<>("outstanding"));
        colInterest.setCellValueFactory(new PropertyValueFactory<>("interestRate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        filterCombo.setItems(FXCollections.observableArrayList("ALL", "ACTIVE", "CLOSED"));
        filterCombo.setValue("ACTIVE");
        filterCombo.setOnAction(e -> loadLoans());

        loanAccountCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Account a) { return a == null ? "" : a.getAccountNumber() + " - " + a.getHolderName(); }
            @Override public Account fromString(String s) { return null; }
        });
        loanSearchField.textProperty().addListener((obs, old, val) -> {
            try {
                loanAccountCombo.setItems(FXCollections.observableArrayList(accService.searchAccounts(val)));
            } catch (Exception ignored) {}
        });

        // Select loan on table click for payment
        loanTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) payLoanIdField.setText(String.valueOf(sel.getId()));
        });

        setupDraftListeners();
        loadDraft();
        loadLoans();
    }

    private void setupDraftListeners() {
        loanAmountField.textProperty().addListener((obs, old, val) -> saveDraft());
        interestRateField.textProperty().addListener((obs, old, val) -> saveDraft());
        installmentField.textProperty().addListener((obs, old, val) -> saveDraft());
        dueDateField.textProperty().addListener((obs, old, val) -> saveDraft());
        loanAccountCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> saveDraft());
    }

    private void saveDraft() {
        Map<String, String> data = new HashMap<>();
        data.put("amount", loanAmountField.getText());
        data.put("rate", interestRateField.getText());
        data.put("installment", installmentField.getText());
        data.put("due", dueDateField.getText());
        Account acc = loanAccountCombo.getValue();
        if (acc != null) {
            data.put("accountId", String.valueOf(acc.getId()));
            data.put("accountNum", acc.getAccountNumber());
        }
        draftManager.saveDraft(FORM_ID, data);
    }

    private void loadDraft() {
        Map<String, String> data = draftManager.getDraft(FORM_ID);
        if (!data.isEmpty()) {
            loanAmountField.setText(data.getOrDefault("amount", ""));
            interestRateField.setText(data.getOrDefault("rate", ""));
            installmentField.setText(data.getOrDefault("installment", ""));
            dueDateField.setText(data.getOrDefault("due", ""));
            String accNum = data.get("accountNum");
            if (accNum != null) {
                loanSearchField.setText(accNum);
            }
        }
    }

    @FXML
    private void handleDisburse() {
        Account acc = loanAccountCombo.getValue();
        if (acc == null) { status("Select an account"); return; }
        try {
            BigDecimal amt  = new BigDecimal(loanAmountField.getText().trim());
            BigDecimal rate = new BigDecimal(interestRateField.getText().trim());
            BigDecimal inst = installmentField.getText().isBlank() ? BigDecimal.ZERO
                              : new BigDecimal(installmentField.getText().trim());
            String due = dueDateField.getText().trim();
            loanService.disburseLoan(acc.getId(), amt, rate, inst, due.isEmpty() ? null : due);
            status("✓ Loan of ₹" + amt + " disbursed to " + acc.getHolderName());
            clearForm();
            draftManager.clearDraft(FORM_ID);
            loadLoans();
        } catch (Exception e) {
            status("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handlePayment() {
        String idStr  = payLoanIdField.getText().trim();
        String amtStr = payAmountField.getText().trim();
        if (idStr.isEmpty() || amtStr.isEmpty()) { status("Enter Loan ID and payment amount"); return; }
        try {
            loanService.recordPayment(Integer.parseInt(idStr), new BigDecimal(amtStr));
            status("✓ Payment of ₹" + amtStr + " recorded for Loan #" + idStr);
            payLoanIdField.clear();
            payAmountField.clear();
            loadLoans();
        } catch (Exception e) {
            status("Error: " + e.getMessage());
        }
    }

    private void loadLoans() {
        try {
            String filter = filterCombo.getValue() == null ? "ALL" : filterCombo.getValue();
            loanTable.setItems(FXCollections.observableArrayList(loanService.getAllLoans(filter)));
        } catch (Exception e) {
            status("Load error: " + e.getMessage());
        }
    }

    private void status(String msg) { statusLabel.setText(msg); }

    private void clearForm() {
        loanSearchField.clear();
        loanAccountCombo.setValue(null);
        loanAmountField.clear();
        interestRateField.clear();
        installmentField.clear();
        dueDateField.clear();
    }
}
