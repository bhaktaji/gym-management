package com.gymkhata.domain;

import java.time.LocalDate;

/**
 * Pure check-in decision logic. No side effects.
 */
public final class CheckinRules {

    private CheckinRules() {
    }

    public enum Decision {
        ALLOWED,
        MEMBER_INACTIVE,
        NO_ACTIVE_MEMBERSHIP,
        MEMBERSHIP_EXPIRED,
        ALREADY_CHECKED_IN_TODAY
    }

    public record Input(String memberStatus, String membershipStatus, LocalDate expiryDate,
                       LocalDate today, boolean alreadyCheckedInToday) {
    }

    public static Decision evaluate(Input in) {
        if (!"ACTIVE".equals(in.memberStatus())) {
            return Decision.MEMBER_INACTIVE;
        }
        if (!"ACTIVE".equals(in.membershipStatus())) {
            return Decision.NO_ACTIVE_MEMBERSHIP;
        }
        if (in.expiryDate() == null || in.expiryDate().isBefore(in.today())) {
            return Decision.MEMBERSHIP_EXPIRED;
        }
        if (in.alreadyCheckedInToday()) {
            return Decision.ALREADY_CHECKED_IN_TODAY;
        }
        return Decision.ALLOWED;
    }
}
