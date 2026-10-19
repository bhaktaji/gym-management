package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceNumberingTest {

    @Test
    void formatsStandardNumber() {
        assertEquals("GK/26-27/000123", InvoiceNumbering.format("GK", "26-27", 123));
        assertEquals("AB/26-27/000001", InvoiceNumbering.format("ab", "26-27", 1));
    }

    @Test
    void rejectsBadPrefix() {
        assertThrows(IllegalArgumentException.class, () -> InvoiceNumbering.format("TOOLONG", "26-27", 1));
        assertThrows(IllegalArgumentException.class, () -> InvoiceNumbering.format("G-K", "26-27", 1));
    }

    @Test
    void rejectsBadFinancialYear() {
        assertThrows(IllegalArgumentException.class, () -> InvoiceNumbering.format("GK", "2026-27", 1));
    }

    @Test
    void validatesNumbers() {
        assertTrue(InvoiceNumbering.isValidNumber("GK/26-27/000123"));
        assertFalse(InvoiceNumbering.isValidNumber("GK/26-27/123"));
        assertFalse(InvoiceNumbering.isValidNumber("GK/27-27/000123"));
        assertFalse(InvoiceNumbering.isValidNumber("TOOLONG/26-27/000123"));
        assertFalse(InvoiceNumbering.isValidNumber(null));
    }
}
