package com.gymkhata.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Pure domain calculator for Personal Training & Referral Commissions.
 *
 * Rules:
 * 1. All money and commission calculations use integer paise and RoundingMode.HALF_UP.
 * 2. PERCENT and SLAB rules always apply to the TAXABLE value (excluding GST), never the GST-inclusive total.
 * 3. Resolution order: Package override -> Trainer default -> Gym default.
 * 4. Partial payments under ON_PAYMENT accrue proportionally using cumulative delta rounding:
 *    accrual(payment_i) = round(totalCommission * cumulativePaid_i / invoiceTotal)
 *                       - round(totalCommission * cumulativePaid_{i-1} / invoiceTotal)
 *    so that partial payments of any split (e.g. 60/40 or 33/33/34) sum to 100% of totalCommission
 *    without 1-paise drift.
 */
public final class CommissionCalculator {

    private CommissionCalculator() {
    }

    /**
     * Resolves the active commission rule in strict precedence order:
     * 1. Package override
     * 2. Trainer rule
     * 3. Gym default rule
     */
    public static CommissionRule resolveRule(CommissionRule packageOverride,
                                             CommissionRule trainerRule,
                                             CommissionRule gymDefault) {
        if (isValidRule(packageOverride)) {
            return packageOverride.copyWithSource(CommissionRule.SOURCE_PACKAGE);
        }
        if (isValidRule(trainerRule)) {
            return trainerRule.copyWithSource(CommissionRule.SOURCE_TRAINER);
        }
        if (isValidRule(gymDefault)) {
            return gymDefault.copyWithSource(CommissionRule.SOURCE_GYM);
        }
        return null;
    }

    public static boolean isValidRule(CommissionRule rule) {
        return rule != null && rule.getType() != null && !rule.getType().isBlank();
    }

    /**
     * Computes total commission in paise for a package/invoice given its taxable value (excluding GST),
     * total session count (optional), and prior monthly taxable base for SLAB evaluation.
     */
    public static long computeTotalPackageCommission(CommissionRule rule,
                                                     long taxableValuePaise,
                                                     Integer totalSessions,
                                                     long priorMonthlyTaxablePaise) {
        if (!isValidRule(rule) || taxableValuePaise < 0) {
            return 0L;
        }
        return switch (rule.getType()) {
            case CommissionRule.TYPE_FLAT -> {
                if (CommissionRule.FLAT_MODE_SESSION.equalsIgnoreCase(rule.getFlatMode())) {
                    int sessions = (totalSessions != null && totalSessions > 0) ? totalSessions : 1;
                    yield Math.max(0L, rule.getFlatAmountPaise()) * sessions;
                }
                yield Math.max(0L, rule.getFlatAmountPaise());
            }
            case CommissionRule.TYPE_PERCENT -> multiplyBpsHalfUp(taxableValuePaise, rule.getRateBps());
            case CommissionRule.TYPE_SLAB -> computeSlabCommission(
                    rule.getSlabs(), priorMonthlyTaxablePaise, taxableValuePaise);
            default -> 0L;
        };
    }

    /**
     * Computes progressive marginal slab commission across monthly cumulative taxable value
     * from priorMonthlyTaxablePaise to (priorMonthlyTaxablePaise + currentTaxablePaise).
     */
    public static long computeSlabCommission(List<CommissionRule.Slab> slabs,
                                             long priorMonthlyTaxablePaise,
                                             long currentTaxablePaise) {
        if (slabs == null || slabs.isEmpty() || currentTaxablePaise <= 0) {
            return 0L;
        }
        List<CommissionRule.Slab> sorted = slabs.stream()
                .sorted(Comparator.comparingLong(s -> s.getUpToTaxablePaise() == null || s.getUpToTaxablePaise() <= 0
                        ? Long.MAX_VALUE
                        : s.getUpToTaxablePaise()))
                .toList();

        long start = Math.max(0L, priorMonthlyTaxablePaise);
        long end = start + currentTaxablePaise;
        long tierLower = 0L;
        long totalCommission = 0L;

        for (CommissionRule.Slab slab : sorted) {
            long tierUpper = (slab.getUpToTaxablePaise() == null || slab.getUpToTaxablePaise() <= 0)
                    ? Long.MAX_VALUE
                    : slab.getUpToTaxablePaise();
            if (end > tierLower && start < tierUpper) {
                long overlapStart = Math.max(start, tierLower);
                long overlapEnd = Math.min(end, tierUpper);
                long taxableInTier = Math.max(0L, overlapEnd - overlapStart);
                totalCommission += multiplyBpsHalfUp(taxableInTier, slab.getRateBps());
            }
            tierLower = tierUpper;
            if (tierLower == Long.MAX_VALUE) {
                break;
            }
        }
        return totalCommission;
    }

    /**
     * Computes proportional commission accrual for a payment under ON_PAYMENT trigger.
     * Uses cumulative delta rounding so multiple partial payments sum to totalPackageCommissionPaise.
     */
    public static long computeProportionalPaymentAccrual(long totalPackageCommissionPaise,
                                                         long invoiceTotalPaise,
                                                         long previouslyPaidPaise,
                                                         long currentPaymentPaise) {
        if (totalPackageCommissionPaise <= 0 || currentPaymentPaise <= 0) {
            return 0L;
        }
        if (invoiceTotalPaise <= 0) {
            return previouslyPaidPaise == 0 ? totalPackageCommissionPaise : 0L;
        }
        long clampedPrev = Math.max(0L, Math.min(invoiceTotalPaise, previouslyPaidPaise));
        long clampedCurr = Math.max(0L, Math.min(invoiceTotalPaise, clampedPrev + currentPaymentPaise));

        long cumulativeBefore = proRataHalfUp(totalPackageCommissionPaise, clampedPrev, invoiceTotalPaise);
        long cumulativeAfter = proRataHalfUp(totalPackageCommissionPaise, clampedCurr, invoiceTotalPaise);
        return Math.max(0L, cumulativeAfter - cumulativeBefore);
    }

    /**
     * Computes commission for the k-th delivered session (1-indexed, 1 <= sessionIndex <= totalSessions)
     * under ON_SESSION_DELIVERED trigger.
     */
    public static long computeSessionDeliveredAccrual(CommissionRule rule,
                                                      long totalPackageCommissionPaise,
                                                      int totalSessions,
                                                      int sessionIndex) {
        if (rule == null || sessionIndex < 1) {
            return 0L;
        }
        if (CommissionRule.TYPE_FLAT.equals(rule.getType())
                && CommissionRule.FLAT_MODE_SESSION.equalsIgnoreCase(rule.getFlatMode())) {
            return Math.max(0L, rule.getFlatAmountPaise());
        }
        if (totalSessions <= 0 || sessionIndex > totalSessions) {
            return 0L;
        }
        long before = proRataHalfUp(totalPackageCommissionPaise, sessionIndex - 1L, totalSessions);
        long after = proRataHalfUp(totalPackageCommissionPaise, sessionIndex, totalSessions);
        return Math.max(0L, after - before);
    }

    /**
     * Multiplies paise by basis points (10000 bps = 100%) using HALF_UP rounding.
     */
    public static long multiplyBpsHalfUp(long amountPaise, int rateBps) {
        if (amountPaise <= 0 || rateBps <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(amountPaise)
                .multiply(BigDecimal.valueOf(rateBps))
                .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static long proRataHalfUp(long totalCommissionPaise, long numerator, long denominator) {
        if (totalCommissionPaise <= 0 || numerator <= 0 || denominator <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(totalCommissionPaise)
                .multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
