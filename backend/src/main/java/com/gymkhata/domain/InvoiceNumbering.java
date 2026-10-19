package com.gymkhata.domain;

/**
 * Gapless invoice numbering per gym per financial year: PFX/26-27/000123.
 * Prefix up to 3 characters; total number at most 16 characters.
 */
public final class InvoiceNumbering {

    public static final int MAX_TOTAL_LENGTH = 16;
    public static final int MAX_PREFIX_LENGTH = 3;
    public static final int SEQUENCE_DIGITS = 6;

    private InvoiceNumbering() {
    }

    public static String format(String prefix, String financialYear, long sequence) {
        String p = normalizePrefix(prefix);
        if (!FinancialYear.isValidLabel(financialYear)) {
            throw new IllegalArgumentException("bad financial year: " + financialYear);
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be >= 1");
        }
        String seq = String.format("%0" + SEQUENCE_DIGITS + "d", sequence);
        String number = p + "/" + financialYear + "/" + seq;
        if (number.length() > MAX_TOTAL_LENGTH) {
            throw new IllegalArgumentException("invoice number exceeds " + MAX_TOTAL_LENGTH + " chars: " + number);
        }
        return number;
    }

    public static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "GK";
        }
        String p = prefix.trim().toUpperCase();
        if (p.length() > MAX_PREFIX_LENGTH || !p.matches("[A-Z0-9]{1,3}")) {
            throw new IllegalArgumentException("prefix must be 1-3 chars [A-Z0-9]: " + prefix);
        }
        return p;
    }

    public static boolean isValidNumber(String number) {
        if (number == null || number.length() > MAX_TOTAL_LENGTH) {
            return false;
        }
        String[] parts = number.split("/");
        if (parts.length != 3) {
            return false;
        }
        return parts[0].matches("[A-Z0-9]{1,3}")
                && FinancialYear.isValidLabel(parts[1])
                && parts[2].matches("\\d{6}");
    }
}
