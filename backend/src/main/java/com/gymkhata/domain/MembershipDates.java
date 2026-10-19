package com.gymkhata.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Membership date math. A plan of N days bought on startDate is valid for N
 * calendar days inclusive: lastValidDay = startDate + N - 1.
 */
public final class MembershipDates {

    private MembershipDates() {
    }

    public static LocalDate expiryDate(LocalDate startDate, int durationDays) {
        if (durationDays < 1) {
            throw new IllegalArgumentException("durationDays must be >= 1");
        }
        return startDate.plusDays(durationDays - 1L);
    }

    public static long daysRemaining(LocalDate expiryDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expiryDate.plusDays(1));
    }
}
