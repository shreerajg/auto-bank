package com.autobank.interest;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

public class InterestCalculationTest {

    private BigDecimal calculateMonthlyInterest(BigDecimal principal, BigDecimal annualRate) {
        if (principal == null || annualRate == null) return BigDecimal.ZERO;
        return principal.multiply(annualRate)
                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                .divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
    }

    @Test
    public void testStandardSavingsInterestCalculation() {
        // Principal = 120,000, Annual Rate = 6.0% -> Annual Interest = 7,200 -> Monthly = 600.00
        BigDecimal principal = new BigDecimal("120000.00");
        BigDecimal rate = new BigDecimal("6.00");
        BigDecimal monthlyInterest = calculateMonthlyInterest(principal, rate);

        assertEquals(new BigDecimal("600.00"), monthlyInterest);
    }

    @Test
    public void testRoundingOnFractionalInterest() {
        // Principal = 10,000, Annual Rate = 7.5% -> Annual Interest = 750 -> Monthly = 62.50
        BigDecimal principal = new BigDecimal("10000.00");
        BigDecimal rate = new BigDecimal("7.50");
        BigDecimal monthlyInterest = calculateMonthlyInterest(principal, rate);

        assertEquals(new BigDecimal("62.50"), monthlyInterest);
    }

    @Test
    public void testZeroPrincipalOrRate() {
        assertEquals(BigDecimal.ZERO, calculateMonthlyInterest(BigDecimal.ZERO, new BigDecimal("8.00")));
        assertEquals(new BigDecimal("0.00"), calculateMonthlyInterest(new BigDecimal("50000.00"), BigDecimal.ZERO));
        assertEquals(BigDecimal.ZERO, calculateMonthlyInterest(null, new BigDecimal("8.00")));
        assertEquals(BigDecimal.ZERO, calculateMonthlyInterest(new BigDecimal("50000.00"), null));
    }
}
