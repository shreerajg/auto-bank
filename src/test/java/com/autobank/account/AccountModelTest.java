package com.autobank.account;

import com.autobank.account.model.Account;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AccountModelTest {

    @Test
    public void testAccountModelCreationAndGetters() {
        Account account = new Account();
        account.setId(1);
        account.setAccountNumber("ACC-1001");
        account.setHolderName("Ramesh Patil");
        account.setPhone("9876543210");
        account.setAddress("Village Kavathe, Sangli");
        account.setBalance(new BigDecimal("25000.50"));
        account.setInterestRate(new BigDecimal("4.50"));
        account.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        account.setCreatedAt(now);

        assertEquals(1, account.getId());
        assertEquals("ACC-1001", account.getAccountNumber());
        assertEquals("Ramesh Patil", account.getHolderName());
        assertEquals("9876543210", account.getPhone());
        assertEquals("Village Kavathe, Sangli", account.getAddress());
        assertEquals(new BigDecimal("25000.50"), account.getBalance());
        assertEquals(new BigDecimal("4.50"), account.getInterestRate());
        assertEquals("ACTIVE", account.getStatus());
        assertEquals(now, account.getCreatedAt());
        assertEquals("ACC-1001 — Ramesh Patil", account.toString());
    }
}
