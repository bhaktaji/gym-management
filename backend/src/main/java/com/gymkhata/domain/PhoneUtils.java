package com.gymkhata.domain;

import java.util.regex.Pattern;

/**
 * Phones are stored E.164 (+91 then 10 digits starting 6-9).
 * Accepts 10-digit, 0-prefixed and +91 input.
 */
public final class PhoneUtils {

    private static final Pattern MOBILE_10 = Pattern.compile("[6-9]\\d{9}");

    private PhoneUtils() {
    }

    /** Returns E.164 form, or null if the input is not a valid Indian mobile. */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("[\\s\\-()]", "");
        if (digits.startsWith("+91")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!MOBILE_10.matcher(digits).matches()) {
            return null;
        }
        return "+91" + digits;
    }

    public static boolean isValid(String input) {
        return normalize(input) != null;
    }

    /** Mask for logs: +919000000001 -> +91*****0001 (country code + last 4 only) */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 8) {
            return "***";
        }
        return e164.substring(0, 3) + "*****" + e164.substring(e164.length() - 4);
    }
}
