package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckinRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void allowsActiveMemberWithValidMembership() {
        CheckinRules.Input in = new CheckinRules.Input("ACTIVE", "ACTIVE", TODAY.plusDays(5), TODAY, false);
        assertEquals(CheckinRules.Decision.ALLOWED, CheckinRules.evaluate(in));
    }

    @Test
    void rejectsInactiveMember() {
        CheckinRules.Input in = new CheckinRules.Input("INACTIVE", "ACTIVE", TODAY.plusDays(5), TODAY, false);
        assertEquals(CheckinRules.Decision.MEMBER_INACTIVE, CheckinRules.evaluate(in));
    }

    @Test
    void rejectsWithoutActiveMembership() {
        CheckinRules.Input in = new CheckinRules.Input("ACTIVE", "EXPIRED", TODAY.plusDays(5), TODAY, false);
        assertEquals(CheckinRules.Decision.NO_ACTIVE_MEMBERSHIP, CheckinRules.evaluate(in));
    }

    @Test
    void rejectsExpiredMembership() {
        CheckinRules.Input in = new CheckinRules.Input("ACTIVE", "ACTIVE", TODAY.minusDays(1), TODAY, false);
        assertEquals(CheckinRules.Decision.MEMBERSHIP_EXPIRED, CheckinRules.evaluate(in));
    }

    @Test
    void rejectsSecondCheckInSameDay() {
        CheckinRules.Input in = new CheckinRules.Input("ACTIVE", "ACTIVE", TODAY.plusDays(5), TODAY, true);
        assertEquals(CheckinRules.Decision.ALREADY_CHECKED_IN_TODAY, CheckinRules.evaluate(in));
    }
}
