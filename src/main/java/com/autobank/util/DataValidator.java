package com.autobank.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Validation utilities for AutoBank cooperative banking.
 * Provides defensive input validation for account numbers, phone numbers,
 * amounts, and other user-supplied data before persistence.
 */
public final class DataValidator {

    private static final Pattern ACCOUNT_NUMBER_PATTERN =
            Pattern.compile("^AC\\d{10,14}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[6-9]\\d{9}$");

    private static final Pattern IFSC_PATTERN =
            Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    private DataValidator() {
        // utility class
    }

    /**
     * Validate an account number. AutoBank account numbers follow the
     * pattern AC + timestamp + random suffix (e.g. AC1718000000100).
     */
    public static boolean isValidAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return false;
        }
        return ACCOUNT_NUMBER_PATTERN.matcher(accountNumber.trim()).matches();
    }

    /**
     * Validate an Indian mobile phone number (10 digits, starts with 6/7/8/9).
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * Validate an IFSC code (e.g. SBIN0001234).
     */
    public static boolean isValidIfsc(String ifsc) {
        if (ifsc == null || ifsc.isBlank()) {
            return false;
        }
        return IFSC_PATTERN.matcher(ifsc.trim().toUpperCase()).matches();
    }

    /**
     * Validate a monetary amount: must be non-null, non-negative, and
     * finite with at most two decimal places.
     */
    public static boolean isValidAmount(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            return false;
        }
        return true;
    }

    /**
     * Validate a monetary amount from a string representation.
     * Returns the parsed BigDecimal if valid, null otherwise.
     */
    public static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(raw.trim());
            if (isValidAmount(amount)) {
                return amount;
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Validate a holder name: non-blank, 2-100 characters, allows
     * letters (Latin and Devanagari), spaces, hyphens, and periods.
     */
    public static boolean isValidHolderName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            return false;
        }
        // Allow letters (including Devanagari), spaces, hyphens, periods, apostrophes
        return trimmed.matches("^[\\p{L}\\s\\-'.]+$");
    }

    /**
     * Validate an email address with a basic regex.
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String trimmed = email.trim();
        if (trimmed.length() > 254) {
            return false;
        }
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 0 || atIndex == trimmed.length() - 1) {
            return false;
        }
        String localPart = trimmed.substring(0, atIndex);
        String domainPart = trimmed.substring(atIndex + 1);
        if (localPart.isBlank() || domainPart.isBlank()) {
            return false;
        }
        if (!domainPart.contains(".")) {
            return false;
        }
        return true;
    }
}
