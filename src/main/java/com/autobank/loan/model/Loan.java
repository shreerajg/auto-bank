package com.autobank.loan.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Loan {
    private int id;
    private int accountId;
    private String accountNumber;
    private String holderName;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private BigDecimal installmentAmount;
    private BigDecimal totalPaid;
    private BigDecimal outstanding;
    private LocalDateTime disbursedAt;
    private LocalDate dueDate;
    private String status;
    private int operatorId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getAccountId() { return accountId; }
    public void setAccountId(int v) { this.accountId = v; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String v) { this.accountNumber = v; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String v) { this.holderName = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal v) { this.interestRate = v; }
    public BigDecimal getInstallmentAmount() { return installmentAmount; }
    public void setInstallmentAmount(BigDecimal v) { this.installmentAmount = v; }
    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal v) { this.totalPaid = v; }
    public BigDecimal getOutstanding() { return outstanding; }
    public void setOutstanding(BigDecimal v) { this.outstanding = v; }
    public LocalDateTime getDisbursedAt() { return disbursedAt; }
    public void setDisbursedAt(LocalDateTime v) { this.disbursedAt = v; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate v) { this.dueDate = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public int getOperatorId() { return operatorId; }
    public void setOperatorId(int v) { this.operatorId = v; }
}
