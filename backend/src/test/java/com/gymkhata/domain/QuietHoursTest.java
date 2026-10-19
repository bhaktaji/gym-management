package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuietHoursTest {

    @Test
    void quietBetween21And08() {
        assertTrue(QuietHours.isQuiet(LocalTime.of(21, 0)));
        assertTrue(QuietHours.isQuiet(LocalTime.of(23, 30)));
        assertTrue(QuietHours.isQuiet(LocalTime.of(0, 0)));
        assertTrue(QuietHours.isQuiet(LocalTime.of(7, 59)));
        assertFalse(QuietHours.isQuiet(LocalTime.of(8, 0)));
        assertFalse(QuietHours.isQuiet(LocalTime.of(12, 0)));
        assertFalse(QuietHours.isQuiet(LocalTime.of(20, 59)));
    }
}
