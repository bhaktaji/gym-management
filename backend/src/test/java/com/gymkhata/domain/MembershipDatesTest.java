package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MembershipDatesTest {

    @Test
    void expiryIsInclusiveLastDay() {
        // 30-day plan from 19 Oct covers 19 Oct..17 Nov (30 days inclusive)
        assertEquals(LocalDate.of(2026, 11, 17), MembershipDates.expiryDate(LocalDate.of(2026, 10, 19), 30));
        assertEquals(LocalDate.of(2026, 10, 19), MembershipDates.expiryDate(LocalDate.of(2026, 10, 19), 1));
    }

    @Test
    void rejectsBadDuration() {
        assertThrows(IllegalArgumentException.class, () -> MembershipDates.expiryDate(LocalDate.of(2026, 10, 19), 0));
    }

    @Test
    void daysRemaining() {
        // valid days including today: 30-day plan has 30 on day 1, 1 on the last day, 0 once expired
        assertEquals(30, MembershipDates.daysRemaining(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 10, 19)));
        assertEquals(1, MembershipDates.daysRemaining(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19)));
        assertEquals(0, MembershipDates.daysRemaining(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 19)));
    }
}
