package com.autobank.transaction;

import com.autobank.transaction.model.Transaction;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionModelTest {

    @Test
    public void testDepositTransactionCalculation() {
        Transaction tx = new Transaction();
        tx.setId(10);
        tx.setAccountId(1);
        tx.setType("DEPOSIT");
        tx.setAmount(new BigDecimal("5000.00"));
        tx.setBalanceBefore(new BigDecimal("10000.00"));
        tx.setBalanceAfter(new BigDecimal("15000.00"));
        tx.setDescription("Dairy milk proceeds deposit");
        tx.setStatus("COMPLETED");
        tx.setOperatorId(2);
        LocalDateTime now = LocalDateTime.now();
        tx.setCreatedAt(now);

        assertEquals(10, tx.getId());
        assertEquals("DEPOSIT", tx.getType());
        assertEquals(new BigDecimal("5000.00"), tx.getAmount());
        assertEquals(new BigDecimal("10000.00"), tx.getBalanceBefore());
        assertEquals(new BigDecimal("15000.00"), tx.getBalanceAfter());
        assertEquals(tx.getBalanceBefore().add(tx.getAmount()), tx.getBalanceAfter());
    }

    @Test
    public void testWithdrawalTransactionCalculation() {
        Transaction tx = new Transaction();
        tx.setId(11);
        tx.setAccountId(1);
        tx.setType("WITHDRAWAL");
        tx.setAmount(new BigDecimal("2000.00"));
        tx.setBalanceBefore(new BigDecimal("15000.00"));
        tx.setBalanceAfter(new BigDecimal("13000.00"));
        tx.setDescription("Cash withdrawal");
        tx.setStatus("COMPLETED");

        assertEquals("WITHDRAWAL", tx.getType());
        assertEquals(tx.getBalanceBefore().subtract(tx.getAmount()), tx.getBalanceAfter());
    }
}
