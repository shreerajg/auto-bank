package com.autobank.deposit.service;

import com.autobank.deposit.model.TermDeposit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DepositCalculatorTest {

    @Test
    void testFdMaturityQuarterlyCompounding() {
        // Principal = 100,000, Rate = 8.0% p.a., Tenure = 12 months (4 quarters)
        // A = 100000 * (1 + 0.08/4)^4 = 100000 * (1.02)^4 = 100000 * 1.08243216 = 108,243.22
        BigDecimal principal = BigDecimal.valueOf(100000);
        BigDecimal rate = BigDecimal.valueOf(8.0);
        int tenureMonths = 12;

        BigDecimal maturity = DepositCalculator.calculateFdMaturity(principal, rate, tenureMonths);
        assertNotNull(maturity);
        assertEquals(BigDecimal.valueOf(108243.22), maturity);
    }

    @Test
    void testFdMaturityShortTenureSimpleInterest() {
        // 2 months tenure (< 3 months) -> simple interest
        // Principal = 50,000, Rate = 6.0%, Tenure = 2 months
        // Interest = 50000 * (6 * 2 / 1200) = 500
        // Maturity = 50,500.00
        BigDecimal principal = BigDecimal.valueOf(50000);
        BigDecimal rate = BigDecimal.valueOf(6.0);
        int tenureMonths = 2;

        BigDecimal maturity = DepositCalculator.calculateFdMaturity(principal, rate, tenureMonths);
        assertEquals(new BigDecimal("50500.00"), maturity);
    }

    @Test
    void testFdZeroRateOrTenure() {
        BigDecimal principal = BigDecimal.valueOf(25000);
        assertEquals(new BigDecimal("25000.00"), DepositCalculator.calculateFdMaturity(principal, BigDecimal.ZERO, 12));
        assertEquals(new BigDecimal("25000.00"), DepositCalculator.calculateFdMaturity(principal, BigDecimal.valueOf(7.5), 0));
    }

    @Test
    void testRdMaturityCalculation() {
        // Monthly installment = 1,000, Rate = 7.5%, Tenure = 12 months
        BigDecimal installment = BigDecimal.valueOf(1000);
        BigDecimal rate = BigDecimal.valueOf(7.5);
        int tenureMonths = 12;

        BigDecimal maturity = DepositCalculator.calculateRdMaturity(installment, rate, tenureMonths);
        assertNotNull(maturity);
        // Total invested = 12,000. Maturity should be greater than total invested
        assertTrue(maturity.compareTo(BigDecimal.valueOf(12000)) > 0);
        assertEquals(new BigDecimal("12495.69"), maturity);
    }

    @Test
    void testPrematureSettlementCalculation() {
        TermDeposit deposit = new TermDeposit();
        deposit.setDepositType("FD");
        deposit.setPrincipalAmount(BigDecimal.valueOf(100000));
        deposit.setTotalDeposited(BigDecimal.valueOf(100000));
        deposit.setInterestRate(BigDecimal.valueOf(8.5));
        deposit.setTenureMonths(24);
        deposit.setDepositDate(LocalDate.now().minusMonths(6));

        // Premature closure after 6 months with 1.0% penalty -> effective rate = 7.5%
        DepositCalculator.PrematureSettlement settlement =
            DepositCalculator.calculatePrematurePayout(deposit, LocalDate.now(), BigDecimal.valueOf(1.0));

        assertNotNull(settlement);
        assertEquals(BigDecimal.valueOf(100000), settlement.getPrincipal());
        assertTrue(settlement.getInterest().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(settlement.getTotalPayout().compareTo(BigDecimal.valueOf(100000)) > 0);
    }
}
