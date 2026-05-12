package com.autobank.distribution.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDistribution {
    private int id;
    private String importFile;
    private LocalDateTime importedAt;
    private BigDecimal totalAmount;
    private int totalRecords;
    private int matchedRecords;
    private String status;
    private int operatorId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getImportFile() { return importFile; }
    public void setImportFile(String importFile) { this.importFile = importFile; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
    public int getMatchedRecords() { return matchedRecords; }
    public void setMatchedRecords(int matchedRecords) { this.matchedRecords = matchedRecords; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getOperatorId() { return operatorId; }
    public void setOperatorId(int operatorId) { this.operatorId = operatorId; }
}
