package com.gymkhata.common.clock;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Injectable clock fixed to Asia/Kolkata. Business dates are LocalDate in IST;
 * instants are stored in UTC.
 */
@Component
public class Clock {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public LocalDate today() {
        return LocalDate.now(IST);
    }

    public LocalTime nowIst() {
        return LocalTime.now(IST);
    }

    public Instant now() {
        return Instant.now();
    }
}
