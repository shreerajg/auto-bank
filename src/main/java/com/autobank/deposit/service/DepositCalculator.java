package com.autobank.deposit.service;

import com.autobank.deposit.model.TermDeposit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class DepositCalculator {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_EVEN);

    /**
     * Calculates the maturity amount for a Fixed Deposit (FD) using quarterly compounding.
     * Formula: A = P * (1 + r/400)^(4 * t/12)
     * For tenure < 3 months, standard simple interest is used: A = P * (1 + (r * t)/1200)
     */
    public static BigDecimal calculateFdMaturity(BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) <= 0) return principal.setScale(2, RoundingMode.HALF_EVEN);
        if (tenureMonths <= 0) return principal.setScale(2, RoundingMode.HALF_EVEN);

        if (tenureMonths < 3) {
            // Simple interest for very short tenures
            BigDecimal rateFraction = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_EVEN);
            BigDecimal interest = principal.multiply(rateFraction).multiply(BigDecimal.valueOf(tenureMonths));
            return principal.add(interest).setScale(2, RoundingMode.HALF_EVEN);
        }

        double p = principal.doubleValue();
        double r = annualRate.doubleValue() / 100.0;
        double quarters = tenureMonths / 3.0; // tenure in quarters

        // A = P * (1 + r/4)^quarters
        double maturity = p * Math.pow(1.0 + (r / 4.0), quarters);
        return BigDecimal.valueOf(maturity).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Calculates the maturity amount for a Recurring Deposit (RD) using quarterly compounding.
     * Each monthly installment earns interest for the remaining tenure.
     */
    public static BigDecimal calculateRdMaturity(BigDecimal monthlyInstallment, BigDecimal annualRate, int tenureMonths) {
        if (monthlyInstallment == null || monthlyInstallment.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) <= 0) {
            return monthlyInstallment.multiply(BigDecimal.valueOf(tenureMonths)).setScale(2, RoundingMode.HALF_EVEN);
        }
        if (tenureMonths <= 0) return BigDecimal.ZERO;

        double p = monthlyInstallment.doubleValue();
        double r = annualRate.doubleValue() / 100.0;
        double totalMaturity = 0.0;

        for (int i = 1; i <= tenureMonths; i++) {
            // Installment paid in month (tenureMonths - i + 1) stays invested for i months
            double quarters = i / 3.0;
            totalMaturity += p * Math.pow(1.0 + (r / 4.0), quarters);
        }

        return BigDecimal.valueOf(totalMaturity).setScale(2, RoundingMode.HALF_EVEN);
    }

    /**
     * Computes the premature settlement payout for an active FD or RD.
     * Applies a penalty rate reduction (e.g. 1.0% p.a. deduction) for premature withdrawal.
     */
    public static PrematureSettlement calculatePrematurePayout(TermDeposit deposit, LocalDate closureDate, BigDecimal penaltyRate) {
        if (deposit == null) {
            return new PrematureSettlement(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }

        LocalDate start = deposit.getDepositDate() != null ? deposit.getDepositDate() : LocalDate.now();
        if (closureDate == null || closureDate.isBefore(start)) {
            closureDate = start;
        }

        long daysElapsed = ChronoUnit.DAYS.between(start, closureDate);
        int monthsElapsed = (int) (daysElapsed / 30);
        if (monthsElapsed <= 0 && daysElapsed > 0) monthsElapsed = 1;

        BigDecimal originalRate = deposit.getInterestRate();
        BigDecimal effectiveRate = originalRate.subtract(penaltyRate != null ? penaltyRate : BigDecimal.valueOf(1.0));
        if (effectiveRate.compareTo(BigDecimal.ZERO) < 0) {
            effectiveRate = BigDecimal.ZERO;
        }

        BigDecimal totalPrincipal = deposit.getTotalDeposited();
        if (totalPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            totalPrincipal = "FD".equalsIgnoreCase(deposit.getDepositType()) ? deposit.getPrincipalAmount() : deposit.getMonthlyInstallment();
        }

        BigDecimal interestEarned;
        if ("FD".equalsIgnoreCase(deposit.getDepositType())) {
            BigDecimal maturityAtElapsed = calculateFdMaturity(totalPrincipal, effectiveRate, monthsElapsed);
            interestEarned = maturityAtElapsed.subtract(totalPrincipal).max(BigDecimal.ZERO);
        } else {
            // RD premature payout
            BigDecimal maturityAtElapsed = calculateRdMaturity(deposit.getMonthlyInstallment(), effectiveRate, monthsElapsed);
            interestEarned = maturityAtElapsed.subtract(totalPrincipal).max(BigDecimal.ZERO);
        }

        BigDecimal totalPayout = totalPrincipal.add(interestEarned).setScale(2, RoundingMode.HALF_EVEN);
        return new PrematureSettlement(totalPrincipal, interestEarned, totalPayout, monthsElapsed);
    }

    public static class PrematureSettlement {
        private final BigDecimal principal;
        private final BigDecimal interest;
        private final BigDecimal totalPayout;
        private final int monthsElapsed;

        public PrematureSettlement(BigDecimal principal, BigDecimal interest, BigDecimal totalPayout, int monthsElapsed) {
            this.principal = principal;
            this.interest = interest;
            this.totalPayout = totalPayout;
            this.monthsElapsed = monthsElapsed;
        }

        public BigDecimal getPrincipal() { return principal; }
        public BigDecimal getInterest() { return interest; }
        public BigDecimal getTotalPayout() { return totalPayout; }
        public int getMonthsElapsed() { return monthsElapsed; }
    }
}
