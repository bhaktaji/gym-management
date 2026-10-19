package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GstCalculatorTest {

    @Test
    void backCalculatesTaxFromInclusiveTotal() {
        // 1180 inclusive @18% -> taxable 1000, tax 180, cgst 90, sgst 90
        GstCalculator.Breakdown b = GstCalculator.split(118000, 18);
        assertEquals(100000, b.taxablePaise());
        assertEquals(18000, b.taxPaise());
        assertEquals(9000, b.cgstPaise());
        assertEquals(9000, b.sgstPaise());
        assertEquals(118000, b.totalPaise());
    }

    @Test
    void fivePercentRateFromSep22_2025() {
        // 1050 inclusive @5% -> taxable 1000, tax 50, cgst 25, sgst 25
        GstCalculator.Breakdown b = GstCalculator.split(105000, 5);
        assertEquals(100000, b.taxablePaise());
        assertEquals(5000, b.taxPaise());
        assertEquals(2500, b.cgstPaise());
        assertEquals(2500, b.sgstPaise());
    }

    @Test
    void linesAlwaysSumExactlyToTotal() {
        for (long total = 1; total <= 100000; total += 37) {
            for (int rate : new int[]{0, 5, 12, 18, 28}) {
                GstCalculator.Breakdown b = GstCalculator.split(total, rate);
                assertEquals(total, b.totalPaise(), "total=" + total + " rate=" + rate);
                assertEquals(b.taxPaise(), b.cgstPaise() + b.sgstPaise() + b.igstPaise());
            }
        }
    }

    @Test
    void interstatePutsAllTaxInIgst() {
        GstCalculator.Breakdown b = GstCalculator.splitInterstate(118000, 18);
        assertEquals(18000, b.igstPaise());
        assertEquals(0, b.cgstPaise());
        assertEquals(0, b.sgstPaise());
        assertEquals(118000, b.totalPaise());
    }

    @Test
    void rejectsNegativeTotal() {
        assertThrows(IllegalArgumentException.class, () -> GstCalculator.split(-1, 18));
    }
}
