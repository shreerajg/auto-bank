package com.autobank.reports.service;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.config.DatabaseConfig;
import com.autobank.transaction.model.Transaction;
import com.autobank.transaction.service.TransactionService;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Export service for generating CSV statements for individual accounts.
 * Produces a plain-text CSV compatible with spreadsheet applications
 * and printable for customer distribution.
 */
public class BankStatementService {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public BankStatementService() throws SQLException {
        this.accountService = new AccountService();
        this.transactionService = new TransactionService();
    }

    /**
     * Export a CSV bank statement for the given account covering the
     * specified date range.
     *
     * @param accountId   the account to export
     * @param startDate   inclusive start of period
     * @param endDate     inclusive end of period
     * @param outputPath  path to write the CSV file
     * @return the number of transactions exported, or -1 on error
     */
    public int exportStatement(int accountId, LocalDate startDate,
                                LocalDate endDate, String outputPath) {
        try {
            Account account = accountService.getAccountById(accountId);
            if (account == null) {
                System.err.println("Account not found: " + accountId);
                return -1;
            }

            List<Transaction> transactions = transactionService
                    .getTransactionsForAccount(accountId, startDate, endDate);

            try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
                // Header
                writer.println("Bank Statement");
                writer.println("=============");
                writer.println("Account: " + account.getAccountNumber());
                writer.println("Holder: " + account.getHolderName());
                writer.println("Period: " + startDate + " to " + endDate);
                writer.println();

                // Column headers
                writer.println("Date,Type,Description,Amount,Balance");

                BigDecimal runningBalance = account.getBalance();
                DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

                for (Transaction tx : transactions) {
                    writer.printf("%s,%s,%s,%.2f,%.2f%n",
                            tx.getCreatedAt().toLocalDate().format(fmt),
                            tx.getType(),
                            escapeCsv(tx.getDescription()),
                            tx.getAmount().doubleValue(),
                            runningBalance.doubleValue());
                    runningBalance = runningBalance.add(tx.getAmount());
                }

                writer.println();
                writer.println("Ending Balance,," + runningBalance.doubleValue());
            }

            System.out.println("Statement exported to: " + outputPath);
            return transactions.size();

        } catch (IOException | SQLException e) {
            System.err.println("Export failed: " + e.getMessage());
            return -1;
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
