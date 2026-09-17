package com.autobank.deposit.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TermDeposit {
    private int id;
    private String depositNumber;
    private int accountId;
    private String accountNumber;
    private String holderName;
    private String depositType; // "FD" or "RD"
    private BigDecimal principalAmount = BigDecimal.ZERO;
    private BigDecimal monthlyInstallment = BigDecimal.ZERO;
    private int tenureMonths;
    private BigDecimal interestRate = BigDecimal.ZERO;
    private BigDecimal maturityAmount = BigDecimal.ZERO;
    private LocalDate depositDate;
    private LocalDate maturityDate;
    private String status; // "ACTIVE", "MATURED", "CLOSED", "PREMATURE_CLOSED"
    private BigDecimal totalDeposited = BigDecimal.ZERO;
    private BigDecimal interestPaid = BigDecimal.ZERO;
    private BigDecimal payoutAmount = BigDecimal.ZERO;
    private LocalDateTime closedAt;
    private int operatorId;
    private LocalDateTime createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDepositNumber() { return depositNumber; }
    public void setDepositNumber(String depositNumber) { this.depositNumber = depositNumber; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getDepositType() { return depositType; }
    public void setDepositType(String depositType) { this.depositType = depositType; }

    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }

    public BigDecimal getMonthlyInstallment() { return monthlyInstallment; }
    public void setMonthlyInstallment(BigDecimal monthlyInstallment) { this.monthlyInstallment = monthlyInstallment; }

    public int getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(int tenureMonths) { this.tenureMonths = tenureMonths; }

    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }

    public BigDecimal getMaturityAmount() { return maturityAmount; }
    public void setMaturityAmount(BigDecimal maturityAmount) { this.maturityAmount = maturityAmount; }

    public LocalDate getDepositDate() { return depositDate; }
    public void setDepositDate(LocalDate depositDate) { this.depositDate = depositDate; }

    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalDeposited() { return totalDeposited; }
    public void setTotalDeposited(BigDecimal totalDeposited) { this.totalDeposited = totalDeposited; }

    public BigDecimal getInterestPaid() { return interestPaid; }
    public void setInterestPaid(BigDecimal interestPaid) { this.interestPaid = interestPaid; }

    public BigDecimal getPayoutAmount() { return payoutAmount; }
    public void setPayoutAmount(BigDecimal payoutAmount) { this.payoutAmount = payoutAmount; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public int getOperatorId() { return operatorId; }
    public void setOperatorId(int operatorId) { this.operatorId = operatorId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isMatured() {
        return maturityDate != null && !LocalDate.now().isBefore(maturityDate) && "ACTIVE".equals(status);
    }

    public boolean isDueSoon(int daysThreshold) {
        if (maturityDate == null || !"ACTIVE".equals(status)) return false;
        LocalDate now = LocalDate.now();
        return !now.isAfter(maturityDate) && !now.plusDays(daysThreshold).isBefore(maturityDate);
    }
}
