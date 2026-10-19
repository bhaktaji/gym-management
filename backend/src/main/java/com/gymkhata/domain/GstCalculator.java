package com.gymkhata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GST back-calculation from a GST-inclusive ("all-in") total.
 * taxable = round(total * 100 / (100 + rate)); tax = total - taxable;
 * CGST = floor(tax / 2); SGST = tax - CGST. Lines always sum exactly to the total.
 */
public final class GstCalculator {

    private GstCalculator() {
    }

    public record Breakdown(long taxablePaise, long taxPaise, long cgstPaise, long sgstPaise, long igstPaise) {

        public long totalPaise() {
            return taxablePaise + taxPaise;
        }
    }

    public static Breakdown split(long totalPaiseInclusive, int ratePercent) {
        if (totalPaiseInclusive < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
        if (ratePercent < 0) {
            throw new IllegalArgumentException("rate must be >= 0");
        }
        if (ratePercent == 0) {
            return new Breakdown(totalPaiseInclusive, 0, 0, 0, 0);
        }
        BigDecimal total = BigDecimal.valueOf(totalPaiseInclusive);
        BigDecimal taxable = total.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(100L + ratePercent), 0, RoundingMode.HALF_UP);
        long taxablePaise = taxable.longValueExact();
        long tax = totalPaiseInclusive - taxablePaise;
        long cgst = tax / 2;
        long sgst = tax - cgst;
        return new Breakdown(taxablePaise, tax, cgst, sgst, 0);
    }

    /** IGST variant for inter-state supply. */
    public static Breakdown splitInterstate(long totalPaiseInclusive, int ratePercent) {
        Breakdown b = split(totalPaiseInclusive, ratePercent);
        return new Breakdown(b.taxablePaise(), b.taxPaise(), 0, 0, b.taxPaise());
    }
}
