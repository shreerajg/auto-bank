package com.autobank.reports.service;

import com.autobank.account.model.Account;
import com.autobank.account.service.AccountService;
import com.autobank.transaction.model.Transaction;
import com.autobank.transaction.service.TransactionService;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
     * @param startDate   inclusive start of period (null = no lower bound)
     * @param endDate     inclusive end of period (null = no upper bound)
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
                writer.println("Bank Statement");
                writer.println("=============");
                writer.println("Account: " + account.getAccountNumber());
                writer.println("Holder: " + account.getHolderName());
                writer.println("Period: " + (startDate != null ? startDate : "All") +
                               " to " + (endDate != null ? endDate : "Present"));
                writer.println();

                writer.println("Date,Type,Description,Debit (Rs),Credit (Rs),Balance After (Rs)");

                DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

                for (Transaction tx : transactions) {
                    boolean isCredit = tx.getType().contains("DEPOSIT") || tx.getType().contains("CREDIT");
                    String debit  = isCredit ? "" : String.format("%.2f", tx.getAmount().doubleValue());
                    String credit = isCredit ? String.format("%.2f", tx.getAmount().doubleValue()) : "";
                    writer.printf("%s,%s,%s,%s,%s,%.2f%n",
                            tx.getCreatedAt().toLocalDate().format(fmt),
                            tx.getType(),
                            escapeCsv(tx.getDescription()),
                            debit,
                            credit,
                            tx.getBalanceAfter().doubleValue());
                }

                writer.println();
                writer.println("Closing Balance,,,,,," + String.format("%.2f", account.getBalance().doubleValue()));
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
