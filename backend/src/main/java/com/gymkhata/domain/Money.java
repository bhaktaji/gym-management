package com.gymkhata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money is always integer paise. Never float/double.
 */
public final class Money {

    private Money() {
    }

    public static long ofRupees(long rupees) {
        return rupees * 100L;
    }

    public static long ofRupees(BigDecimal rupees) {
        return rupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static long roundToPaise(BigDecimal rupees) {
        return rupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Indian digit grouping: last 3 digits, then groups of 2. 12345600 paise -> "1,23,456.00".
     */
    public static String formatIndian(long paise) {
        boolean negative = paise < 0;
        long abs = Math.abs(paise);
        long rupees = abs / 100;
        long p = abs % 100;
        String grouped = groupIndian(rupees);
        return (negative ? "-" : "") + grouped + "." + String.format("%02d", p);
    }

    public static String formatRupees(long rupees) {
        return groupIndian(rupees) + ".00";
    }

    private static String groupIndian(long value) {
        String s = Long.toString(value);
        if (s.length() <= 3) {
            return s;
        }
        String last3 = s.substring(s.length() - 3);
        String head = s.substring(0, s.length() - 3);
        StringBuilder sb = new StringBuilder();
        while (head.length() > 2) {
            sb.insert(0, head.substring(head.length() - 2));
            sb.insert(0, ',');
            head = head.substring(0, head.length() - 2);
        }
        sb.insert(0, head);
        return sb + "," + last3;
    }
}
