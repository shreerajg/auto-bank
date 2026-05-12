package com.autobank.account.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Account {
    private int id;
    private String accountNumber;
    private String holderName;
    private String phone;
    private String address;
    private BigDecimal balance;
    private String status;
    private LocalDateTime createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String v) { this.accountNumber = v; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String v) { this.holderName = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal v) { this.balance = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    @Override
    public String toString() {
        return accountNumber + " — " + holderName;
    }
}
