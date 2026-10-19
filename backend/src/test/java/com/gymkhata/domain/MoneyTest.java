package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyTest {

    @Test
    void formatsWithIndianGrouping() {
        assertEquals("0.00", Money.formatIndian(0));
        assertEquals("99.00", Money.formatIndian(9900));
        assertEquals("1,23,456.00", Money.formatIndian(12345600));
        assertEquals("1,00,000.00", Money.formatIndian(10000000));
        assertEquals("12,34,56,789.00", Money.formatIndian(12345678900L));
        assertEquals("-1,234.56", Money.formatIndian(-123456));
    }

    @Test
    void convertsRupeesToPaise() {
        assertEquals(99900L, Money.ofRupees(999));
        assertEquals(118000L, Money.ofRupees(new java.math.BigDecimal("1180")));
    }
}
