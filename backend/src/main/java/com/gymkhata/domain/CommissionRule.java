package com.gymkhata.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Value object representing a trainer commission rule.
 * Snapshotted immutably onto every CommissionEntry so future rate changes
 * never alter historical commission ledger entries.
 */
public class CommissionRule {

    public static final String TYPE_FLAT = "FLAT";
    public static final String TYPE_PERCENT = "PERCENT";
    public static final String TYPE_SLAB = "SLAB";

    public static final String FLAT_MODE_PACKAGE = "PER_PACKAGE";
    public static final String FLAT_MODE_SESSION = "PER_SESSION";

    public static final String SOURCE_PACKAGE = "PACKAGE_OVERRIDE";
    public static final String SOURCE_TRAINER = "TRAINER_DEFAULT";
    public static final String SOURCE_GYM = "GYM_DEFAULT";

    private String type; // FLAT, PERCENT, SLAB
    private String flatMode = FLAT_MODE_PACKAGE; // PER_PACKAGE or PER_SESSION
    private long flatAmountPaise;
    /** Percentage in basis points (e.g. 2000 = 20.00%, 1250 = 12.50%). */
    private int rateBps;
    private List<Slab> slabs = new ArrayList<>();
    private String source; // PACKAGE_OVERRIDE, TRAINER_DEFAULT, GYM_DEFAULT
    private Instant effectiveFrom;

    public CommissionRule() {
    }

    public static CommissionRule flatPerPackage(long flatAmountPaise) {
        CommissionRule r = new CommissionRule();
        r.setType(TYPE_FLAT);
        r.setFlatMode(FLAT_MODE_PACKAGE);
        r.setFlatAmountPaise(flatAmountPaise);
        r.setEffectiveFrom(Instant.now());
        return r;
    }

    public static CommissionRule flatPerSession(long flatAmountPaise) {
        CommissionRule r = new CommissionRule();
        r.setType(TYPE_FLAT);
        r.setFlatMode(FLAT_MODE_SESSION);
        r.setFlatAmountPaise(flatAmountPaise);
        r.setEffectiveFrom(Instant.now());
        return r;
    }

    public static CommissionRule percentOfTaxable(int rateBps) {
        CommissionRule r = new CommissionRule();
        r.setType(TYPE_PERCENT);
        r.setRateBps(rateBps);
        r.setEffectiveFrom(Instant.now());
        return r;
    }

    public static CommissionRule slabRule(List<Slab> slabs) {
        CommissionRule r = new CommissionRule();
        r.setType(TYPE_SLAB);
        r.setSlabs(slabs == null ? List.of() : new ArrayList<>(slabs));
        r.setEffectiveFrom(Instant.now());
        return r;
    }

    public CommissionRule copyWithSource(String resolvedSource) {
        CommissionRule copy = new CommissionRule();
        copy.setType(this.type);
        copy.setFlatMode(this.flatMode);
        copy.setFlatAmountPaise(this.flatAmountPaise);
        copy.setRateBps(this.rateBps);
        if (this.slabs != null) {
            List<Slab> copiedSlabs = new ArrayList<>();
            for (Slab s : this.slabs) {
                copiedSlabs.add(new Slab(s.getUpToTaxablePaise(), s.getRateBps()));
            }
            copy.setSlabs(copiedSlabs);
        }
        copy.setSource(resolvedSource);
        copy.setEffectiveFrom(this.effectiveFrom != null ? this.effectiveFrom : Instant.now());
        return copy;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFlatMode() {
        return flatMode;
    }

    public void setFlatMode(String flatMode) {
        this.flatMode = flatMode;
    }

    public long getFlatAmountPaise() {
        return flatAmountPaise;
    }

    public void setFlatAmountPaise(long flatAmountPaise) {
        this.flatAmountPaise = flatAmountPaise;
    }

    public int getRateBps() {
        return rateBps;
    }

    public void setRateBps(int rateBps) {
        this.rateBps = rateBps;
    }

    public List<Slab> getSlabs() {
        return slabs;
    }

    public void setSlabs(List<Slab> slabs) {
        this.slabs = slabs;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    /**
     * A progressive slab tier on monthly cumulative taxable value (in paise).
     * upToTaxablePaise == null or <= 0 means unbounded (top tier).
     */
    public static class Slab {
        private Long upToTaxablePaise;
        private int rateBps;

        public Slab() {
        }

        public Slab(Long upToTaxablePaise, int rateBps) {
            this.upToTaxablePaise = upToTaxablePaise;
            this.rateBps = rateBps;
        }

        public Long getUpToTaxablePaise() {
            return upToTaxablePaise;
        }

        public void setUpToTaxablePaise(Long upToTaxablePaise) {
            this.upToTaxablePaise = upToTaxablePaise;
        }

        public int getRateBps() {
            return rateBps;
        }

        public void setRateBps(int rateBps) {
            this.rateBps = rateBps;
        }
    }
}
