package com.gymkhata.domain;

/**
 * Amount in words using the Indian numbering system (crore / lakh / thousand / hundred).
 * Used on GST invoices. e.g. 118000 paise -> "One Thousand One Hundred Eighty Rupees Only".
 */
public final class IndianNumberWords {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private IndianNumberWords() {
    }

    public static String amountInWords(long paise) {
        if (paise < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        long rupees = paise / 100;
        int p = (int) (paise % 100);
        StringBuilder sb = new StringBuilder();
        if (rupees == 0 && p == 0) {
            return "Zero Rupees Only";
        }
        if (rupees > 0) {
            sb.append(group(rupees)).append(" Rupees");
        }
        if (p > 0) {
            if (rupees > 0) {
                sb.append(" and ");
            }
            sb.append(underHundred(p)).append(" Paise");
        }
        sb.append(" Only");
        return sb.toString();
    }

    private static String group(long n) {
        if (n == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        long crore = n / 10000000;
        long lakh = (n % 10000000) / 100000;
        long thousand = (n % 100000) / 1000;
        long rest = n % 1000;
        if (crore > 0) {
            sb.append(group(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            sb.append(underHundred((int) lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            sb.append(underHundred((int) thousand)).append(" Thousand ");
        }
        if (rest > 0) {
            sb.append(underThousand((int) rest));
        }
        return sb.toString().trim();
    }

    private static String underThousand(int n) {
        if (n < 100) {
            return underHundred(n);
        }
        int hundreds = n / 100;
        int rest = n % 100;
        return rest == 0 ? ONES[hundreds] + " Hundred" : ONES[hundreds] + " Hundred " + underHundred(rest);
    }

    private static String underHundred(int n) {
        if (n < 20) {
            return ONES[n];
        }
        int tens = n / 10;
        int ones = n % 10;
        return ones == 0 ? TENS[tens] : TENS[tens] + " " + ONES[ones];
    }
}
