package com.gymkhata.domain;

import java.time.LocalTime;

/**
 * WhatsApp quiet hours: 21:00-08:00 IST.
 */
public final class QuietHours {

    public static final LocalTime START = LocalTime.of(21, 0);
    public static final LocalTime END = LocalTime.of(8, 0);

    private QuietHours() {
    }

    public static boolean isQuiet(LocalTime istTime) {
        if (istTime == null) {
            return false;
        }
        return !istTime.isBefore(START) || istTime.isBefore(END);
    }
}
