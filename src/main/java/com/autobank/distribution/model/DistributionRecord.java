package com.autobank.distribution.model;

import java.math.BigDecimal;

public class DistributionRecord {
    private int id;
    private int distributionId;
    private Integer accountId;
    private String holderName;
    private BigDecimal amount;
    private String status;
    private String errorMessage;
    private Integer transactionId;
    
    // Virtual field for UI matching
    private String accountNumber;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getDistributionId() { return distributionId; }
    public void setDistributionId(int distributionId) { this.distributionId = distributionId; }
    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getTransactionId() { return transactionId; }
    public void setTransactionId(Integer transactionId) { this.transactionId = transactionId; }
}
