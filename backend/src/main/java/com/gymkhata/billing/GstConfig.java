package com.gymkhata.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Effective-dated GST rates. The rate in force on the invoice date is
 * snapshotted onto each invoice line and never recomputed later.
 * Default: 18% before 22 Sep 2025, 5% from 22 Sep 2025 (no input tax credit).
 */
@Component
@ConfigurationProperties(prefix = "app.gst")
public class GstConfig {

    private String sac = "999723";
    private List<RateEntry> rates = List.of(
            new RateEntry(18, LocalDate.of(2000, 1, 1)),
            new RateEntry(5, LocalDate.of(2025, 9, 22)));

    public String getSac() {
        return sac;
    }

    public void setSac(String sac) {
        this.sac = sac;
    }

    public List<RateEntry> getRates() {
        return rates;
    }

    public void setRates(List<RateEntry> rates) {
        this.rates = rates;
    }

    /** Rate in force on the given date (latest effective-from <= date). */
    public int rateFor(LocalDate date) {
        return rates.stream()
                .filter(r -> !r.effectiveFrom().isAfter(date))
                .max((a, b) -> a.effectiveFrom().compareTo(b.effectiveFrom()))
                .map(RateEntry::rate)
                .orElse(0);
    }

    public record RateEntry(int rate, LocalDate effectiveFrom) {
    }
}
