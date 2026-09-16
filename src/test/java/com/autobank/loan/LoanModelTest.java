package com.autobank.loan;

import com.autobank.loan.model.Loan;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class LoanModelTest {

    @Test
    public void testLoanModelProperties() {
        Loan loan = new Loan();
        loan.setId(5);
        loan.setAccountId(2);
        loan.setAccountNumber("ACC-2002");
        loan.setHolderName("Suresh Jadhav");
        loan.setAmount(new BigDecimal("50000.00"));
        loan.setInterestRate(new BigDecimal("12.00"));
        loan.setInstallmentAmount(new BigDecimal("4500.00"));
        loan.setTotalPaid(new BigDecimal("18000.00"));
        loan.setOutstanding(new BigDecimal("35000.00"));
        loan.setDisbursedAt(LocalDateTime.now());
        loan.setDueDate(LocalDate.now().plusMonths(12));
        loan.setStatus("ACTIVE");
        loan.setOperatorId(1);

        assertEquals(5, loan.getId());
        assertEquals("ACC-2002", loan.getAccountNumber());
        assertEquals("Suresh Jadhav", loan.getHolderName());
        assertEquals(new BigDecimal("50000.00"), loan.getAmount());
        assertEquals(new BigDecimal("12.00"), loan.getInterestRate());
        assertEquals(new BigDecimal("4500.00"), loan.getInstallmentAmount());
        assertEquals(new BigDecimal("18000.00"), loan.getTotalPaid());
        assertEquals(new BigDecimal("35000.00"), loan.getOutstanding());
        assertEquals("ACTIVE", loan.getStatus());
        assertNotNull(loan.getDueDate());
    }
}
