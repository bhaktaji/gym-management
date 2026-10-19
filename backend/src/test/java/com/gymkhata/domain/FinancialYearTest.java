package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialYearTest {

    @Test
    void aprilStartsNewFinancialYear() {
        assertEquals("26-27", FinancialYear.fyOf(LocalDate.of(2026, 4, 1)));
        assertEquals("26-27", FinancialYear.fyOf(LocalDate.of(2026, 9, 22)));
        assertEquals("25-26", FinancialYear.fyOf(LocalDate.of(2026, 3, 31)));
        assertEquals("25-26", FinancialYear.fyOf(LocalDate.of(2025, 4, 1)));
    }

    @Test
    void startAndEndDates() {
        assertEquals(LocalDate.of(2026, 4, 1), FinancialYear.fyStart("26-27"));
        assertEquals(LocalDate.of(2027, 3, 31), FinancialYear.fyEnd("26-27"));
    }

    @Test
    void validatesLabels() {
        assertTrue(FinancialYear.isValidLabel("26-27"));
        assertTrue(FinancialYear.isValidLabel("99-00"));
        assertFalse(FinancialYear.isValidLabel("27-27"));
        assertFalse(FinancialYear.isValidLabel("2026-27"));
        assertFalse(FinancialYear.isValidLabel(null));
    }
}
