package com.autobank.loan.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LoanPayment {
    private int id;
    private int loanId;
    private BigDecimal amount;
    private int operatorId;
    private LocalDateTime paidAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getLoanId() { return loanId; }
    public void setLoanId(int v) { this.loanId = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public int getOperatorId() { return operatorId; }
    public void setOperatorId(int v) { this.operatorId = v; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime v) { this.paidAt = v; }
}
