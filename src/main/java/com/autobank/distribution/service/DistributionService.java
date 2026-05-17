package com.autobank.distribution.service;

import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.autobank.distribution.model.DistributionRecord;
import com.autobank.distribution.model.PaymentDistribution;
import com.autobank.transaction.model.Transaction;
import com.autobank.transaction.service.TransactionService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DistributionService {
    private static final Logger log = LoggerFactory.getLogger(DistributionService.class);
    private final TransactionService transactionService = new TransactionService();

    public PaymentDistribution parseFile(String filePath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("python", "python/excel_handler.py", filePath);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }
        
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            log.error("Python exit code {}. Output: {}", exitCode, output);
            throw new Exception("Python script failed with exit code " + exitCode);
        }

        JsonObject json = JsonParser.parseString(output.toString()).getAsJsonObject();
        if (json.has("error")) throw new Exception(json.get("error").getAsString());

        PaymentDistribution dist = new PaymentDistribution();
        dist.setImportFile(filePath);
        dist.setTotalAmount(json.get("total_amount").getAsBigDecimal());
        dist.setTotalRecords(json.get("total_records").getAsInt());
        dist.setStatus("PENDING");
        dist.setOperatorId(UserSession.getInstance().getCurrentUser().getId());

        saveDistribution(dist, json.getAsJsonArray("records"));
        return dist;
    }

    private void saveDistribution(PaymentDistribution dist, JsonArray records) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String sqlDist = "INSERT INTO payment_distributions (import_file, total_amount, total_records, operator_id) VALUES (?, ?, ?, ?)";
                PreparedStatement psDist = conn.prepareStatement(sqlDist, Statement.RETURN_GENERATED_KEYS);
                psDist.setString(1, dist.getImportFile());
                psDist.setBigDecimal(2, dist.getTotalAmount());
                psDist.setInt(3, dist.getTotalRecords());
                psDist.setInt(4, dist.getOperatorId());
                psDist.executeUpdate();
                ResultSet rs = psDist.getGeneratedKeys();
                if (rs.next()) dist.setId(rs.getInt(1));

                String sqlRec = "INSERT INTO distribution_records (distribution_id, account_id, holder_name, amount, status) VALUES (?, ?, ?, ?, 'PENDING')";
                PreparedStatement psRec = conn.prepareStatement(sqlRec);
                
                int matched = 0;
                for (var element : records) {
                    JsonObject r = element.getAsJsonObject();
                    String accNum = r.has("account_number") ? r.get("account_number").getAsString() : "";
                    Integer accId = findAccountId(conn, accNum);
                    
                    psRec.setInt(1, dist.getId());
                    if (accId != null) {
                        psRec.setInt(2, accId);
                        matched++;
                    } else {
                        psRec.setNull(2, Types.INTEGER);
                    }
                    psRec.setString(3, r.get("name").getAsString());
                    psRec.setBigDecimal(4, r.get("amount").getAsBigDecimal());
                    psRec.addBatch();
                }
                psRec.executeBatch();
                
                // Update matched count
                PreparedStatement upd = conn.prepareStatement("UPDATE payment_distributions SET matched_records = ? WHERE id = ?");
                upd.setInt(1, matched);
                upd.setInt(2, dist.getId());
                upd.executeUpdate();
                dist.setMatchedRecords(matched);

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private Integer findAccountId(Connection conn, String accNum) throws SQLException {
        if (accNum == null || accNum.isBlank()) return null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM accounts WHERE account_number = ?")) {
            ps.setString(1, accNum);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : null;
        }
    }

    public List<DistributionRecord> getRecords(int distId) throws SQLException {
        List<DistributionRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT dr.*, a.account_number FROM distribution_records dr " +
                 "LEFT JOIN accounts a ON dr.account_id = a.id " +
                 "WHERE dr.distribution_id = ?")) {
            ps.setInt(1, distId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                DistributionRecord r = new DistributionRecord();
                r.setId(rs.getInt("id"));
                r.setDistributionId(rs.getInt("distribution_id"));
                r.setAccountId((Integer) rs.getObject("account_id"));
                r.setHolderName(rs.getString("holder_name"));
                r.setAccountNumber(rs.getString("account_number"));
                r.setAmount(rs.getBigDecimal("amount"));
                r.setStatus(rs.getString("status"));
                r.setErrorMessage(rs.getString("error_message"));
                r.setTransactionId((Integer) rs.getObject("transaction_id"));
                list.add(r);
            }
        }
        return list;
    }

    public void processDistribution(int distId) throws SQLException {
        List<DistributionRecord> records = getPendingRecords(distId);
        for (DistributionRecord r : records) {
            if (r.getAccountId() != null) {
                try {
                    Transaction tx = transactionService.deposit(r.getAccountId(), r.getAmount(), "Bulk Distribution #" + distId);
                    updateRecordStatus(r.getId(), "CREDITED", null, tx.getId());
                } catch (Exception e) {
                    updateRecordStatus(r.getId(), "FAILED", e.getMessage(), null);
                }
            } else {
                updateRecordStatus(r.getId(), "FAILED", "No matching account", null);
            }
        }
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE payment_distributions SET status = 'COMPLETED' WHERE id = ?")) {
            ps.setInt(1, distId);
            ps.executeUpdate();
        }
    }

    private List<DistributionRecord> getPendingRecords(int distId) throws SQLException {
        List<DistributionRecord> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM distribution_records WHERE distribution_id = ? AND status = 'PENDING'")) {
            ps.setInt(1, distId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                DistributionRecord r = new DistributionRecord();
                r.setId(rs.getInt("id"));
                r.setAccountId((Integer) rs.getObject("account_id"));
                r.setAmount(rs.getBigDecimal("amount"));
                list.add(r);
            }
        }
        return list;
    }

    public PaymentDistribution getLatestPendingDistribution() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM payment_distributions WHERE status = 'PENDING' ORDER BY imported_at DESC LIMIT 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PaymentDistribution dist = new PaymentDistribution();
                dist.setId(rs.getInt("id"));
                dist.setImportFile(rs.getString("import_file"));
                dist.setTotalAmount(rs.getBigDecimal("total_amount"));
                dist.setTotalRecords(rs.getInt("total_records"));
                dist.setMatchedRecords(rs.getInt("matched_records"));
                dist.setStatus(rs.getString("status"));
                return dist;
            }
        }
        return null;
    }

    private void updateRecordStatus(int recId, String status, String error, Integer txId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE distribution_records SET status = ?, error_message = ?, transaction_id = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, error);
            if (txId != null) ps.setInt(3, txId); else ps.setNull(3, Types.INTEGER);
            ps.setInt(4, recId);
            ps.executeUpdate();
        }
    }
}
