package com.gymkhata.domain;

import java.time.LocalDate;

/**
 * Indian financial year: 1 April to 31 March, in Asia/Kolkata.
 * FY label format "YY-YY", e.g. 1 Apr 2026 -> "26-27".
 */
public final class FinancialYear {

    private FinancialYear() {
    }

    public static String fyOf(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return label(startYear);
    }

    public static String label(int startYear) {
        return String.format("%02d-%02d", startYear % 100, (startYear + 1) % 100);
    }

    public static LocalDate fyStart(String fy) {
        return LocalDate.of(startYear(fy), 4, 1);
    }

    public static LocalDate fyEnd(String fy) {
        return LocalDate.of(startYear(fy) + 1, 3, 31);
    }

    public static int startYear(String fy) {
        String[] parts = fy.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("bad FY label: " + fy);
        }
        int start = Integer.parseInt(parts[0]);
        return start < 100 ? 2000 + start : start;
    }

    public static boolean isValidLabel(String fy) {
        if (fy == null || !fy.matches("\\d{2}-\\d{2}")) {
            return false;
        }
        try {
            String[] parts = fy.split("-");
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            return (a + 1) % 100 == b % 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
