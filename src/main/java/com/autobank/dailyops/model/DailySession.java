package com.autobank.dailyops.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DailySession {
    private int id;
    private LocalDate date;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal expectedCash;
    private BigDecimal actualCash;
    private String status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate v) { this.date = v; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal v) { this.openingBalance = v; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public void setClosingBalance(BigDecimal v) { this.closingBalance = v; }
    public BigDecimal getExpectedCash() { return expectedCash; }
    public void setExpectedCash(BigDecimal v) { this.expectedCash = v; }
    public BigDecimal getActualCash() { return actualCash; }
    public void setActualCash(BigDecimal v) { this.actualCash = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime v) { this.openedAt = v; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime v) { this.closedAt = v; }
}
