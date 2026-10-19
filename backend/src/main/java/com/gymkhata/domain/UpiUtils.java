package com.gymkhata.domain;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * UPI helpers: VPA validation, upi://pay deep links, UTR validation.
 */
public final class UpiUtils {

    private static final Pattern VPA = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,}@[a-zA-Z]{2,}$");
    private static final Pattern UTR = Pattern.compile("\\d{12}");

    private UpiUtils() {
    }

    public static boolean isValidVpa(String vpa) {
        return vpa != null && VPA.matcher(vpa.trim()).matches();
    }

    public static boolean isValidUtr(String utr) {
        return utr != null && UTR.matcher(utr.trim()).matches();
    }

    public static String amountToRupees(long paise) {
        return new java.math.BigDecimal(paise)
                .movePointLeft(2)
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    /** Standard UPI deep link: upi://pay?pa=...&pn=...&am=...&tn=...&cu=INR */
    public static String buildDeepLink(String vpa, String payeeName, long amountPaise, String note) {
        StringBuilder sb = new StringBuilder("upi://pay?");
        sb.append("pa=").append(url(vpa));
        sb.append("&pn=").append(url(payeeName == null ? "" : payeeName));
        sb.append("&am=").append(url(amountToRupees(amountPaise)));
        sb.append("&cu=INR");
        if (note != null && !note.isBlank()) {
            sb.append("&tn=").append(url(note));
        }
        return sb.toString();
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
