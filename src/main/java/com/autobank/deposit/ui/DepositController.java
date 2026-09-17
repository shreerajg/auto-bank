package com.autobank.deposit.ui;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.deposit.model.TermDeposit;
import com.autobank.deposit.service.DepositCalculator;
import com.autobank.deposit.service.DepositService;
import com.autobank.ui.MainController;
import com.autobank.util.I18n;
import com.autobank.util.Toast;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class DepositController {

    private static final Logger log = LoggerFactory.getLogger(DepositController.class);

    // Stats
    @FXML private Label fdVolumeLabel;
    @FXML private Label rdVolumeLabel;
    @FXML private Label dueSoonLabel;
    @FXML private Label totalActiveLabel;

    // Filters & Search
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private ComboBox<String> typeFilterCombo;

    // Table
    @FXML private TableView<TermDeposit> depositTable;
    @FXML private TableColumn<TermDeposit, String> colDepositNumber;
    @FXML private TableColumn<TermDeposit, String> colHolder;
    @FXML private TableColumn<TermDeposit, String> colType;
    @FXML private TableColumn<TermDeposit, String> colPrincipal;
    @FXML private TableColumn<TermDeposit, String> colRate;
    @FXML private TableColumn<TermDeposit, String> colTenure;
    @FXML private TableColumn<TermDeposit, String> colMaturityAmt;
    @FXML private TableColumn<TermDeposit, String> colMaturityDate;
    @FXML private TableColumn<TermDeposit, String> colDeposited;
    @FXML private TableColumn<TermDeposit, String> colStatus;

    @FXML private Label statusMessageLabel;

    // Tabs & Forms
    @FXML private TabPane operationsTabPane;

    // Tab 1: Open Deposit
    @FXML private TextField openSearchMemberField;
    @FXML private ComboBox<Account> openAccountCombo;
    @FXML private ComboBox<String> openTypeCombo;
    @FXML private TextField openTenureField;
    @FXML private Label openAmountLabel;
    @FXML private TextField openAmountField;
    @FXML private TextField openInterestRateField;
    @FXML private CheckBox openDeductSavingsCheck;
    @FXML private Label previewMaturityLabel;
    @FXML private Label previewInterestLabel;
    @FXML private Label previewDateLabel;

    // Tab 2: Pay RD Installment
    @FXML private TextField rdDepositIdField;
    @FXML private Label rdDetailsLabel;
    @FXML private CheckBox rdDeductSavingsCheck;

    // Tab 3: Settlement
    @FXML private TextField settleDepositIdField;
    @FXML private Label settleSummaryLabel;
    @FXML private Label settleCalculationsLabel;
    @FXML private TextField settlePenaltyField;
    @FXML private CheckBox settleCreditSavingsCheck;

    private final DepositService depositService = new DepositService();
    private final AccountService accountService = new AccountService();
    private final ObservableList<TermDeposit> tableData = FXCollections.observableArrayList();
    private final NumberFormat currencyFmt = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private TermDeposit selectedDeposit = null;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilters();
        setupOpenForm();
        setupTableSelection();
        loadDeposits();
        refreshStats();
    }

    private void setupTableColumns() {
        colDepositNumber.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDepositNumber()));
        colHolder.setCellValueFactory(d -> new SimpleStringProperty(
            (d.getValue().getHolderName() != null ? d.getValue().getHolderName() : "") +
            " (" + (d.getValue().getAccountNumber() != null ? d.getValue().getAccountNumber() : "") + ")"));

        colType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDepositType()));
        colPrincipal.setCellValueFactory(d -> new SimpleStringProperty(
            "₹" + (d.getValue().getPrincipalAmount() != null ? d.getValue().getPrincipalAmount().toPlainString() : "0.00")));
        colRate.setCellValueFactory(d -> new SimpleStringProperty(
            (d.getValue().getInterestRate() != null ? d.getValue().getInterestRate().toPlainString() : "0") + "%"));
        colTenure.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTenureMonths() + " M"));
        colMaturityAmt.setCellValueFactory(d -> new SimpleStringProperty(
            "₹" + (d.getValue().getMaturityAmount() != null ? d.getValue().getMaturityAmount().toPlainString() : "0.00")));
        colMaturityDate.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getMaturityDate() != null ? d.getValue().getMaturityDate().format(dateFmt) : "-"));
        colDeposited.setCellValueFactory(d -> new SimpleStringProperty(
            "₹" + (d.getValue().getTotalDeposited() != null ? d.getValue().getTotalDeposited().toPlainString() : "0.00")));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    TermDeposit td = getTableRow().getItem();
                    Label badge = new Label(td.getStatus());
                    badge.getStyleClass().add("badge");
                    if ("ACTIVE".equalsIgnoreCase(td.getStatus())) {
                        if (td.isDueSoon(30)) {
                            badge.getStyleClass().add("badge-orange");
                            badge.setText("DUE SOON");
                        } else {
                            badge.getStyleClass().add("badge-green");
                        }
                    } else if ("CLOSED".equalsIgnoreCase(td.getStatus())) {
                        badge.getStyleClass().add("badge-blue");
                    } else if ("PREMATURE_CLOSED".equalsIgnoreCase(td.getStatus())) {
                        badge.getStyleClass().add("badge-red");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        depositTable.setItems(tableData);
    }

    private void setupFilters() {
        statusFilterCombo.setItems(FXCollections.observableArrayList("ALL", "ACTIVE", "DUE_SOON", "CLOSED", "PREMATURE_CLOSED"));
        statusFilterCombo.setValue("ALL");
        statusFilterCombo.valueProperty().addListener((obs, oldV, newV) -> loadDeposits());

        typeFilterCombo.setItems(FXCollections.observableArrayList("ALL", "FD", "RD"));
        typeFilterCombo.setValue("ALL");
        typeFilterCombo.valueProperty().addListener((obs, oldV, newV) -> loadDeposits());

        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.isBlank()) {
                loadDeposits();
            } else {
                try {
                    List<TermDeposit> results = depositService.searchDeposits(newV.trim());
                    tableData.setAll(results);
                } catch (Exception e) {
                    log.error("Search failed", e);
                }
            }
        });
    }

    private void setupOpenForm() {
        openTypeCombo.setItems(FXCollections.observableArrayList("Fixed Deposit (FD)", "Recurring Deposit (RD)"));
        openTypeCombo.setValue("Fixed Deposit (FD)");

        openTypeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.contains("RD")) {
                openAmountLabel.setText("MONTHLY INSTALLMENT (₹)");
                openAmountField.setPromptText("e.g. 1000");
                if (openTenureField.getText().isBlank()) openTenureField.setText("12");
                if (openInterestRateField.getText().isBlank()) openInterestRateField.setText("7.5");
            } else {
                openAmountLabel.setText("PRINCIPAL AMOUNT (₹)");
                openAmountField.setPromptText("e.g. 50000");
                if (openTenureField.getText().isBlank()) openTenureField.setText("12");
                if (openInterestRateField.getText().isBlank()) openInterestRateField.setText("8.0");
            }
            updateLivePreview();
        });

        openSearchMemberField.textProperty().addListener((obs, oldV, newV) -> searchAccountsForOpen(newV));

        // Auto calculate on any input change
        openAmountField.textProperty().addListener((obs, oldV, newV) -> updateLivePreview());
        openTenureField.textProperty().addListener((obs, oldV, newV) -> updateLivePreview());
        openInterestRateField.textProperty().addListener((obs, oldV, newV) -> updateLivePreview());

        // Defaults
        openTenureField.setText("12");
        openInterestRateField.setText("8.0");
        openAmountField.setText("50000");
        updateLivePreview();
    }

    private void searchAccountsForOpen(String query) {
        try {
            List<Account> list = accountService.searchAccounts(query != null ? query.trim() : "");
            openAccountCombo.setItems(FXCollections.observableArrayList(list));
            if (!list.isEmpty() && (query != null && !query.isBlank())) {
                openAccountCombo.getSelectionModel().selectFirst();
            }
        } catch (Exception e) {
            log.error("Account search error", e);
        }
    }

    private void updateLivePreview() {
        try {
            String amtStr = openAmountField.getText().trim();
            String tenureStr = openTenureField.getText().trim();
            String rateStr = openInterestRateField.getText().trim();

            if (amtStr.isEmpty() || tenureStr.isEmpty() || rateStr.isEmpty()) {
                previewMaturityLabel.setText("₹0.00");
                previewInterestLabel.setText("₹0.00");
                previewDateLabel.setText("-");
                return;
            }

            BigDecimal amt = new BigDecimal(amtStr);
            int tenure = Integer.parseInt(tenureStr);
            BigDecimal rate = new BigDecimal(rateStr);

            LocalDate maturityDate = LocalDate.now().plusMonths(tenure);
            previewDateLabel.setText(maturityDate.format(dateFmt));

            boolean isFd = openTypeCombo.getValue() != null && openTypeCombo.getValue().contains("FD");
            BigDecimal maturity;
            BigDecimal totalInvested;

            if (isFd) {
                maturity = DepositCalculator.calculateFdMaturity(amt, rate, tenure);
                totalInvested = amt;
            } else {
                maturity = DepositCalculator.calculateRdMaturity(amt, rate, tenure);
                totalInvested = amt.multiply(BigDecimal.valueOf(tenure));
            }

            BigDecimal interest = maturity.subtract(totalInvested).max(BigDecimal.ZERO);
            previewMaturityLabel.setText("₹" + maturity.setScale(2, RoundingMode.HALF_EVEN).toPlainString());
            previewInterestLabel.setText("₹" + interest.setScale(2, RoundingMode.HALF_EVEN).toPlainString());

        } catch (Exception e) {
            previewMaturityLabel.setText("₹0.00");
            previewInterestLabel.setText("₹0.00");
            previewDateLabel.setText("-");
        }
    }

    private void setupTableSelection() {
        depositTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                selectedDeposit = newV;
                // Update RD tab
                rdDepositIdField.setText(String.valueOf(newV.getId()));
                if ("RD".equalsIgnoreCase(newV.getDepositType())) {
                    rdDetailsLabel.setText(String.format("RD #%s (%s) — Installment: ₹%s, Total Paid: ₹%s, Tenure: %d Months",
                        newV.getDepositNumber(), newV.getHolderName(), newV.getMonthlyInstallment(), newV.getTotalDeposited(), newV.getTenureMonths()));
                } else {
                    rdDetailsLabel.setText("Selected deposit (" + newV.getDepositNumber() + ") is an FD (Lump-sum), not an RD.");
                }

                // Update Settle tab
                settleDepositIdField.setText(String.valueOf(newV.getId()));
                settleSummaryLabel.setText(String.format("%s Scheme #%s — %s (Acct #%s)",
                    newV.getDepositType(), newV.getDepositNumber(), newV.getHolderName(), newV.getAccountNumber()));

                LocalDate today = LocalDate.now();
                boolean matured = newV.isMatured();
                settleCalculationsLabel.setText(String.format("Status: %s | Deposited: ₹%s | Maturity Amt: ₹%s | Maturity Date: %s (%s)",
                    newV.getStatus(), newV.getTotalDeposited(), newV.getMaturityAmount(),
                    newV.getMaturityDate() != null ? newV.getMaturityDate().format(dateFmt) : "-",
                    matured ? "MATURED" : "RUNNING"));
            }
        });
    }

    private void loadDeposits() {
        try {
            String status = statusFilterCombo.getValue();
            String type = typeFilterCombo.getValue();
            List<TermDeposit> list = depositService.getAllDeposits(status, type);
            tableData.setAll(list);
            statusMessageLabel.setText("Showing " + list.size() + " deposit scheme records");
        } catch (Exception e) {
            log.error("Failed to load deposits", e);
            statusMessageLabel.setText("Error loading deposits: " + e.getMessage());
        }
    }

    private void refreshStats() {
        try {
            DepositService.DepositStats stats = depositService.getDepositStats();
            fdVolumeLabel.setText("₹" + stats.getFdVolume().setScale(2, RoundingMode.HALF_EVEN).toPlainString());
            rdVolumeLabel.setText("₹" + stats.getRdVolume().setScale(2, RoundingMode.HALF_EVEN).toPlainString());
            dueSoonLabel.setText(String.valueOf(stats.getDueSoonCount()));
            totalActiveLabel.setText(String.valueOf(stats.getActiveCount()));
        } catch (Exception e) {
            log.error("Failed to refresh deposit stats", e);
        }
    }

    @FXML
    public void handleRefresh() {
        loadDeposits();
        refreshStats();
        MainController.showToast("Deposits refreshed", Toast.Type.INFO);
    }

    @FXML
    public void handleOpenDeposit() {
        Account acct = openAccountCombo.getValue();
        if (acct == null) {
            MainController.showToast("Please select an account first", Toast.Type.ERROR);
            return;
        }

        try {
            String type = openTypeCombo.getValue().contains("FD") ? "FD" : "RD";
            BigDecimal amt = new BigDecimal(openAmountField.getText().trim());
            int tenure = Integer.parseInt(openTenureField.getText().trim());
            BigDecimal rate = new BigDecimal(openInterestRateField.getText().trim());
            boolean deduct = openDeductSavingsCheck.isSelected();

            if (amt.compareTo(BigDecimal.ZERO) <= 0 || tenure <= 0 || rate.compareTo(BigDecimal.ZERO) < 0) {
                MainController.showToast("Please enter valid amount, tenure, and interest rate", Toast.Type.ERROR);
                return;
            }

            TermDeposit td = depositService.openDeposit(
                acct.getId(), type, amt, amt, tenure, rate, deduct
            );

            MainController.showToast(I18n.t("deposits.msg.created") + " (" + td.getDepositNumber() + ")", Toast.Type.SUCCESS);
            loadDeposits();
            refreshStats();

            // Select created deposit
            depositTable.getSelectionModel().select(td);

        } catch (Exception e) {
            log.error("Failed to open deposit", e);
            MainController.showToast("Error: " + e.getMessage(), Toast.Type.ERROR);
        }
    }

    @FXML
    public void handlePayRdInstallment() {
        String idStr = rdDepositIdField.getText().trim();
        if (idStr.isEmpty()) {
            MainController.showToast("Please enter or select a Deposit ID", Toast.Type.ERROR);
            return;
        }

        try {
            int depositId = Integer.parseInt(idStr);
            boolean deduct = rdDeductSavingsCheck.isSelected();
            depositService.payRdInstallment(depositId, deduct);

            MainController.showToast(I18n.t("deposits.msg.rd_paid"), Toast.Type.SUCCESS);
            loadDeposits();
            refreshStats();

        } catch (Exception e) {
            log.error("Failed to pay RD installment", e);
            MainController.showToast("Error: " + e.getMessage(), Toast.Type.ERROR);
        }
    }

    @FXML
    public void handleNormalSettlement() {
        String idStr = settleDepositIdField.getText().trim();
        if (idStr.isEmpty()) {
            MainController.showToast("Please enter or select a Deposit ID", Toast.Type.ERROR);
            return;
        }

        try {
            int depositId = Integer.parseInt(idStr);
            boolean credit = settleCreditSavingsCheck.isSelected();

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Normal Settlement");
            confirm.setHeaderText("Settle Deposit #" + depositId + " at Full Maturity");
            confirm.setContentText("Proceed with closing this deposit and paying out full maturity proceeds?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                depositService.settleDeposit(depositId, false, BigDecimal.ZERO, credit);
                MainController.showToast(I18n.t("deposits.msg.settled"), Toast.Type.SUCCESS);
                loadDeposits();
                refreshStats();
            }
        } catch (Exception e) {
            log.error("Failed to settle deposit", e);
            MainController.showToast("Error: " + e.getMessage(), Toast.Type.ERROR);
        }
    }

    @FXML
    public void handlePrematureSettlement() {
        String idStr = settleDepositIdField.getText().trim();
        if (idStr.isEmpty()) {
            MainController.showToast("Please enter or select a Deposit ID", Toast.Type.ERROR);
            return;
        }

        try {
            int depositId = Integer.parseInt(idStr);
            BigDecimal penalty = new BigDecimal(settlePenaltyField.getText().trim());
            boolean credit = settleCreditSavingsCheck.isSelected();

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Premature Closure");
            confirm.setHeaderText("Premature Closure for Deposit #" + depositId);
            confirm.setContentText("A penalty reduction of " + penalty + "% will be deducted from the interest. Proceed with premature payout?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                depositService.settleDeposit(depositId, true, penalty, credit);
                MainController.showToast("Deposit closed prematurely and settled", Toast.Type.SUCCESS);
                loadDeposits();
                refreshStats();
            }
        } catch (Exception e) {
            log.error("Failed premature settlement", e);
            MainController.showToast("Error: " + e.getMessage(), Toast.Type.ERROR);
        }
    }

    @FXML
    public void handleShowCertificate() {
        TermDeposit d = selectedDeposit;
        if (d == null) {
            MainController.showToast("Please select a deposit from the table to view certificate", Toast.Type.ERROR);
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Term Deposit Certificate / ठेव प्रमाणपत्र - " + d.getDepositNumber());

        VBox certBox = new VBox(14);
        certBox.setStyle("-fx-background-color: #ffffff; -fx-padding: 28px; -fx-border-color: #3b82f6; -fx-border-width: 2px; -fx-border-radius: 12px;");
        certBox.setAlignment(Pos.CENTER);

        Label headerTitle = new Label("🏦 AUTOBANK COOPERATIVE CREDIT SOCIETY");
        headerTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e3a8a;");

        Label certHeading = new Label(d.getDepositType().equals("FD") ? "FIXED DEPOSIT CERTIFICATE / मुदत ठेव प्रमाणपत्र" : "RECURRING DEPOSIT CERTIFICATE / आवर्ती ठेव प्रमाणपत्र");
        certHeading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 0 0 10 0;");

        TextArea certText = new TextArea();
        certText.setEditable(false);
        certText.setPrefRowCount(14);
        certText.setPrefColumnCount(50);
        certText.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 13px;");

        StringBuilder sb = new StringBuilder();
        sb.append("===============================================================\n");
        sb.append(" CERTIFICATE NO : ").append(d.getDepositNumber()).append("\n");
        sb.append(" MEMBER NAME    : ").append(d.getHolderName()).append("\n");
        sb.append(" ACCOUNT NUMBER : ").append(d.getAccountNumber()).append("\n");
        sb.append(" SCHEME TYPE    : ").append(d.getDepositType().equals("FD") ? "Fixed Deposit (मुदत ठेव)" : "Recurring Deposit (आवर्ती ठेव)").append("\n");
        sb.append(" PRINCIPAL / INST : ₹").append(d.getPrincipalAmount()).append("\n");
        sb.append(" TENURE (MONTHS): ").append(d.getTenureMonths()).append(" Months\n");
        sb.append(" INTEREST RATE  : ").append(d.getInterestRate()).append("% p.a. (Quarterly Compounding)\n");
        sb.append(" DEPOSIT DATE   : ").append(d.getDepositDate() != null ? d.getDepositDate().format(dateFmt) : "-").append("\n");
        sb.append(" MATURITY DATE  : ").append(d.getMaturityDate() != null ? d.getMaturityDate().format(dateFmt) : "-").append("\n");
        sb.append(" MATURITY VALUE : ₹").append(d.getMaturityAmount()).append("\n");
        sb.append(" STATUS         : ").append(d.getStatus()).append("\n");
        sb.append("===============================================================\n");
        sb.append(" Verified and Issued by AutoBank Society Operations Engine\n");
        certText.setText(sb.toString());

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("primary-button");
        closeBtn.setOnAction(e -> dialog.close());

        certBox.getChildren().addAll(headerTitle, certHeading, certText, closeBtn);

        Scene scene = new Scene(certBox, 600, 480);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}
