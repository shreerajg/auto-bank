package com.autobank.deposit;

import com.autobank.deposit.model.DepositInstallment;
import com.autobank.deposit.model.TermDeposit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DepositModelTest {

    @Test
    void testTermDepositMaturityChecks() {
        TermDeposit deposit = new TermDeposit();
        deposit.setStatus("ACTIVE");
        deposit.setDepositDate(LocalDate.now().minusMonths(12));
        deposit.setMaturityDate(LocalDate.now().minusDays(1));

        assertTrue(deposit.isMatured());

        deposit.setMaturityDate(LocalDate.now().plusDays(10));
        assertFalse(deposit.isMatured());
        assertTrue(deposit.isDueSoon(30));
        assertFalse(deposit.isDueSoon(5));

        deposit.setStatus("CLOSED");
        assertFalse(deposit.isMatured());
        assertFalse(deposit.isDueSoon(30));
    }

    @Test
    void testDepositInstallmentFields() {
        DepositInstallment inst = new DepositInstallment();
        inst.setId(1);
        inst.setDepositId(10);
        inst.setInstallmentNumber(2);
        inst.setAmount(BigDecimal.valueOf(1500));
        inst.setPaymentDate(LocalDate.now());

        assertEquals(1, inst.getId());
        assertEquals(10, inst.getDepositId());
        assertEquals(2, inst.getInstallmentNumber());
        assertEquals(BigDecimal.valueOf(1500), inst.getAmount());
        assertEquals(LocalDate.now(), inst.getPaymentDate());
    }
}
