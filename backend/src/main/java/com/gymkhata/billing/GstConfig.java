package com.gymkhata.billing;

import com.gymkhata.admin.model.Gym;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Effective-dated GST rates and SAC codes configurable globally (application.yml)
 * and per gym per item type (MEMBERSHIP, PT, ADMISSION_FEE).
 * The rate, SAC code, and ITC status in force on the invoice date are
 * snapshotted onto each invoice line and never recomputed later.
 *
 * Defaults (confirm with your Chartered Accountant / CA):
 * - SAC 999723 (Physical fitness & gym services)
 * - 18% (With ITC) prior to 2025-09-22
 * - 5% (No ITC) effective 2025-09-22
 */
@Component
@ConfigurationProperties(prefix = "app.gst")
public class GstConfig {

    public static final String ITEM_MEMBERSHIP = "MEMBERSHIP";
    public static final String ITEM_PT = "PT";
    public static final String ITEM_ADMISSION_FEE = "ADMISSION_FEE";

    public static final String DEFAULT_SAC = "999723";
    public static final String ITC_NO = "No ITC";
    public static final String ITC_YES = "With ITC";
    public static final String ITC_NA = "N/A";

    private String sac = DEFAULT_SAC;
    private List<RateEntry> rates = List.of(
            new RateEntry(18, LocalDate.of(2000, 1, 1), ITC_YES),
            new RateEntry(5, LocalDate.of(2025, 9, 22), ITC_NO));

    private Map<String, ItemTypeConfig> items = new LinkedHashMap<>();

    public GstConfig() {
        items.put(ITEM_MEMBERSHIP, defaultItemConfig());
        items.put(ITEM_PT, defaultItemConfig());
        items.put(ITEM_ADMISSION_FEE, defaultItemConfig());
    }

    private static ItemTypeConfig defaultItemConfig() {
        ItemTypeConfig cfg = new ItemTypeConfig();
        cfg.setSac(DEFAULT_SAC);
        cfg.setRates(List.of(
                new RateEntry(18, LocalDate.of(2000, 1, 1), ITC_YES),
                new RateEntry(5, LocalDate.of(2025, 9, 22), ITC_NO)
        ));
        return cfg;
    }

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

    public Map<String, ItemTypeConfig> getItems() {
        return items;
    }

    public void setItems(Map<String, ItemTypeConfig> items) {
        this.items = items;
    }

    /** Rate in force on the given date (latest effective-from <= date). */
    public int rateFor(LocalDate date) {
        return resolveEntry(rates, date).rate();
    }

    public ResolvedTaxRule resolveFor(Gym gym, String itemType, String explicitSacOverride, LocalDate invoiceDate) {
        String normType = normalizeItemType(itemType);
        LocalDate d = invoiceDate != null ? invoiceDate : LocalDate.now();

        // 1. Resolve global item-type default
        ItemTypeConfig itemCfg = findItemConfig(normType);
        List<RateEntry> baseRates = (itemCfg != null && itemCfg.getRates() != null && !itemCfg.getRates().isEmpty())
                ? itemCfg.getRates()
                : this.rates;
        RateEntry activeGlobalEntry = resolveEntry(baseRates, d);

        String resolvedSac = (itemCfg != null && itemCfg.getSac() != null && !itemCfg.getSac().isBlank())
                ? itemCfg.getSac().trim()
                : (this.sac != null && !this.sac.isBlank() ? this.sac.trim() : DEFAULT_SAC);
        int resolvedRate = activeGlobalEntry.rate();
        LocalDate resolvedEffectiveFrom = activeGlobalEntry.effectiveFrom();
        String resolvedItc = activeGlobalEntry.itcStatus();

        // 2. Apply per-gym Settings overrides (if configured for this itemType and effective on/before invoiceDate)
        if (gym != null && gym.getSettings() != null) {
            Gym.Settings s = gym.getSettings();
            if (ITEM_PT.equals(normType) && s.getPtSacCode() != null && !s.getPtSacCode().isBlank()) {
                resolvedSac = s.getPtSacCode().trim();
            }
            Gym.GstItemRule gymRule = s.getGstRuleFor(normType);
            if (gymRule != null) {
                if (gymRule.getSac() != null && !gymRule.getSac().isBlank()) {
                    resolvedSac = gymRule.getSac().trim();
                }
                boolean effectiveActive = gymRule.getEffectiveFrom() == null || !gymRule.getEffectiveFrom().isAfter(d);
                if (effectiveActive) {
                    if (gymRule.getGstRate() != null && gymRule.getGstRate() >= 0) {
                        resolvedRate = gymRule.getGstRate();
                    }
                    if (gymRule.getEffectiveFrom() != null) {
                        resolvedEffectiveFrom = gymRule.getEffectiveFrom();
                    }
                    if (gymRule.getItcStatus() != null && !gymRule.getItcStatus().isBlank()) {
                        resolvedItc = gymRule.getItcStatus().trim();
                    } else if (gymRule.getGstRate() != null) {
                        resolvedItc = defaultItcForRate(gymRule.getGstRate());
                    }
                }
            }
        }

        // 3. Apply explicit package-level SAC override (e.g. PT package SAC) if provided
        if (explicitSacOverride != null && !explicitSacOverride.isBlank()) {
            resolvedSac = explicitSacOverride.trim();
        }

        if (resolvedItc == null || resolvedItc.isBlank()) {
            resolvedItc = defaultItcForRate(resolvedRate);
        }

        return new ResolvedTaxRule(normType, resolvedSac, resolvedRate, resolvedEffectiveFrom, resolvedItc);
    }

    private ItemTypeConfig findItemConfig(String normType) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, ItemTypeConfig> e : items.entrySet()) {
            if (normalizeItemType(e.getKey()).equals(normType)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static String normalizeItemType(String itemType) {
        if (itemType == null || itemType.isBlank()) {
            return ITEM_MEMBERSHIP;
        }
        String u = itemType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (u.contains("PT") || u.contains("PERSONAL")) return ITEM_PT;
        if (u.contains("ADMISSION") || u.contains("JOINING")) return ITEM_ADMISSION_FEE;
        return ITEM_MEMBERSHIP;
    }

    private RateEntry resolveEntry(List<RateEntry> list, LocalDate date) {
        if (list == null || list.isEmpty()) {
            return new RateEntry(0, LocalDate.of(2000, 1, 1), ITC_NA);
        }
        return list.stream()
                .filter(r -> r.effectiveFrom() == null || !r.effectiveFrom().isAfter(date))
                .max(Comparator.comparing(r -> r.effectiveFrom() == null ? LocalDate.MIN : r.effectiveFrom()))
                .orElse(new RateEntry(0, LocalDate.of(2000, 1, 1), ITC_NA));
    }

    public static String defaultItcForRate(int rate) {
        if (rate <= 0) return ITC_NA;
        if (rate == 5) return ITC_NO;
        return ITC_YES;
    }

    public record ResolvedTaxRule(
            String itemType,
            String sac,
            int gstRate,
            LocalDate effectiveFrom,
            String itcStatus
    ) {
    }

    public static class ItemTypeConfig {
        private String sac = DEFAULT_SAC;
        private List<RateEntry> rates = List.of(
                new RateEntry(18, LocalDate.of(2000, 1, 1), ITC_YES),
                new RateEntry(5, LocalDate.of(2025, 9, 22), ITC_NO));

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
    }

    public record RateEntry(int rate, LocalDate effectiveFrom, String itcStatus) {
        public RateEntry(int rate, LocalDate effectiveFrom) {
            this(rate, effectiveFrom, defaultItcForRate(rate));
        }

        @org.springframework.boot.context.properties.bind.ConstructorBinding
        public RateEntry {
            if (itcStatus == null || itcStatus.isBlank()) {
                itcStatus = defaultItcForRate(rate);
            }
        }
    }
}
