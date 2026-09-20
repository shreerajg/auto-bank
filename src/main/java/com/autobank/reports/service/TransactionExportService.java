package com.autobank.reports.service;

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
 * Export service for bulk transaction exports.
 * Generates CSV files suitable for Excel import and audit trails.
 */
public class TransactionExportService {

    private final TransactionService transactionService;

    public TransactionExportService() throws SQLException {
        this.transactionService = new TransactionService();
    }

    /**
     * Export all transactions for a date range to a CSV file.
     *
     * @param startDate   inclusive start of period
     * @param endDate     inclusive end of period
     * @param outputPath  path to write the CSV file
     * @return the number of transactions exported, or -1 on error
     */
    public int exportTransactions(LocalDate startDate, LocalDate endDate,
                                   String outputPath) {
        try {
            List<Transaction> transactions = transactionService
                    .getTransactionsBetween(startDate, endDate);

            try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
                // Header
                writer.println("AutoBank Transaction Export");
                writer.println("Generated: " + LocalDate.now());
                writer.println("Period: " + startDate + " to " + endDate);
                writer.println();

                // Column headers
                writer.println("ID,Account ID,Type,Amount,Balance Before,Balance After,Description,Status,Operator ID,Created At");

                DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

                for (Transaction tx : transactions) {
                    writer.printf("%d,%d,%s,%.2f,%.2f,%.2f,%s,%s,%d,%s%n",
                            tx.getId(),
                            tx.getAccountId(),
                            tx.getType(),
                            tx.getAmount().doubleValue(),
                            tx.getBalanceBefore().doubleValue(),
                            tx.getBalanceAfter().doubleValue(),
                            escapeCsv(tx.getDescription()),
                            tx.getStatus(),
                            tx.getOperatorId(),
                            tx.getCreatedAt().format(fmt));
                }
            }

            System.out.println("Export complete: " + outputPath);
            return transactions.size();

        } catch (IOException | SQLException e) {
            System.err.println("Export failed: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Export transactions for a single account to CSV.
     */
    public int exportAccountTransactions(int accountId, LocalDate startDate,
                                         LocalDate endDate, String outputPath) {
        try {
            List<Transaction> transactions = transactionService
                    .getTransactionsForAccount(accountId, startDate, endDate);

            try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
                writer.println("Account Transactions Export");
                writer.println("Account ID: " + accountId);
                writer.println("Period: " + startDate + " to " + endDate);
                writer.println();

                writer.println("ID,Type,Amount,Balance Before,Balance After,Description,Status,Created At");

                DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

                for (Transaction tx : transactions) {
                    writer.printf("%d,%s,%.2f,%.2f,%.2f,%s,%s,%s%n",
                            tx.getId(),
                            tx.getType(),
                            tx.getAmount().doubleValue(),
                            tx.getBalanceBefore().doubleValue(),
                            tx.getBalanceAfter().doubleValue(),
                            escapeCsv(tx.getDescription()),
                            tx.getStatus(),
                            tx.getCreatedAt().format(fmt));
                }
            }

            System.out.println("Export complete: " + outputPath);
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
