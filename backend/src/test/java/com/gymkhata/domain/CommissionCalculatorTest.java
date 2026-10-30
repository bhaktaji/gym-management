package com.gymkhata.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommissionCalculatorTest {

    @Test
    void flatCommissionPerPackageAndPerSession() {
        // FLAT ₹1,500 per package regardless of sessions
        CommissionRule flatPkg = CommissionRule.flatPerPackage(150_000L);
        assertEquals(150_000L, CommissionCalculator.computeTotalPackageCommission(flatPkg, 500_000L, 12, 0L));

        // FLAT ₹250 per session * 12 sessions = ₹3,000 (300,000 paise)
        CommissionRule flatSess = CommissionRule.flatPerSession(25_000L);
        assertEquals(300_000L, CommissionCalculator.computeTotalPackageCommission(flatSess, 500_000L, 12, 0L));
        assertEquals(25_000L, CommissionCalculator.computeSessionDeliveredAccrual(flatSess, 300_000L, 12, 1));
    }

    @Test
    void percentCommissionOnTaxableValueExcludingGstAndHalfUpRounding() {
        // ₹5,900 inclusive @ 18% GST -> taxable value = ₹5,000 (500,000 paise)
        // 20% (2000 bps) of taxable value = ₹1,000 (100,000 paise), NOT 20% of ₹5,900
        CommissionRule pct20 = CommissionRule.percentOfTaxable(2000);
        assertEquals(100_000L, CommissionCalculator.computeTotalPackageCommission(pct20, 500_000L, 10, 0L));

        // Awkward taxable value: 282,458 paise * 17.5% (1750 bps) = 49,430.15 -> rounds HALF_UP to 49,430 paise
        CommissionRule pct175 = CommissionRule.percentOfTaxable(1750);
        assertEquals(49_430L, CommissionCalculator.computeTotalPackageCommission(pct175, 282_458L, 1, 0L));

        // Exact half paise: 100,001 paise * 50% (5000 bps) = 50,000.5 -> rounds HALF_UP to 50,001 paise
        CommissionRule pct50 = CommissionRule.percentOfTaxable(5000);
        assertEquals(50_001L, CommissionCalculator.computeTotalPackageCommission(pct50, 100_001L, 1, 0L));
    }

    @Test
    void slabCommissionMarginalProgressiveTiersAcrossMonthlyCumulativeTotal() {
        // Tier 1: up to ₹50,000 (5,000,000 paise) @ 10% (1000 bps)
        // Tier 2: ₹50,000 to ₹1,00,000 (10,000,000 paise) @ 20% (2000 bps)
        // Tier 3: above ₹1,00,000 (unbounded = 0) @ 30% (3000 bps)
        CommissionRule slabRule = CommissionRule.slabRule(List.of(
                new CommissionRule.Slab(5_000_000L, 1000),
                new CommissionRule.Slab(10_000_000L, 2000),
                new CommissionRule.Slab(0L, 3000)
        ));

        // Sale 1: ₹30,000 taxable (prior = 0) -> entirely in Tier 1 (10%) = ₹3,000 (300,000 paise)
        long c1 = CommissionCalculator.computeTotalPackageCommission(slabRule, 3_000_000L, 10, 0L);
        assertEquals(300_000L, c1);

        // Sale 2: ₹40,000 taxable (prior = ₹30,000, new cumulative = ₹70,000)
        // ₹20,000 in Tier 1 (10% = ₹2,000) + ₹20,000 in Tier 2 (20% = ₹4,000) = ₹6,000 (600,000 paise)
        long c2 = CommissionCalculator.computeTotalPackageCommission(slabRule, 4_000_000L, 10, 3_000_000L);
        assertEquals(600_000L, c2);

        // Sale 3: ₹50,000 taxable (prior = ₹70,000, new cumulative = ₹1,20,000)
        // ₹30,000 in Tier 2 (20% = ₹6,000) + ₹20,000 in Tier 3 (30% = ₹6,000) = ₹12,000 (1,200,000 paise)
        long c3 = CommissionCalculator.computeTotalPackageCommission(slabRule, 5_000_000L, 10, 7_000_000L);
        assertEquals(1_200_000L, c3);
    }

    @Test
    void proportionalPartialPaymentsSumExactlyToTotalCommissionWithNoRoundingDrift() {
        // Total commission = ₹1,000 (100,000 paise) on ₹5,900 invoice (590,000 paise)
        long totalCommission = 100_000L;
        long invoiceTotal = 590_000L;

        // 60/40 split: ₹3,540 (354,000 paise) then ₹2,360 (236,000 paise)
        long accrual60 = CommissionCalculator.computeProportionalPaymentAccrual(
                totalCommission, invoiceTotal, 0L, 354_000L);
        assertEquals(60_000L, accrual60);

        long accrual40 = CommissionCalculator.computeProportionalPaymentAccrual(
                totalCommission, invoiceTotal, 354_000L, 236_000L);
        assertEquals(40_000L, accrual40);
        assertEquals(totalCommission, accrual60 + accrual40);

        // 3-way awkward split on 100,000 paise commission across 300,000 paise invoice (100,000 each = 1/3 each)
        long p1 = CommissionCalculator.computeProportionalPaymentAccrual(100_000L, 300_000L, 0L, 100_000L);
        long p2 = CommissionCalculator.computeProportionalPaymentAccrual(100_000L, 300_000L, 100_000L, 100_000L);
        long p3 = CommissionCalculator.computeProportionalPaymentAccrual(100_000L, 300_000L, 200_000L, 100_000L);
        assertEquals(33_333L, p1);
        assertEquals(33_334L, p2);
        assertEquals(33_333L, p3);
        assertEquals(100_000L, p1 + p2 + p3);
    }

    @Test
    void resolutionOrderPrioritizesPackageOverrideThenTrainerThenGymDefaultAndSnapshotsImmutably() {
        CommissionRule pkgRule = CommissionRule.percentOfTaxable(3000);
        CommissionRule trainerRule = CommissionRule.percentOfTaxable(2000);
        CommissionRule gymRule = CommissionRule.percentOfTaxable(1000);

        CommissionRule resolved1 = CommissionCalculator.resolveRule(pkgRule, trainerRule, gymRule);
        assertEquals("PACKAGE_OVERRIDE", resolved1.getSource());
        assertEquals(3000, resolved1.getRateBps());

        CommissionRule resolved2 = CommissionCalculator.resolveRule(null, trainerRule, gymRule);
        assertEquals("TRAINER_DEFAULT", resolved2.getSource());
        assertEquals(2000, resolved2.getRateBps());

        CommissionRule resolved3 = CommissionCalculator.resolveRule(null, null, gymRule);
        assertEquals("GYM_DEFAULT", resolved3.getSource());
        assertEquals(1000, resolved3.getRateBps());

        // Mutating original trainerRule after snapshot must not alter resolved2
        trainerRule.setRateBps(500);
        assertEquals(2000, resolved2.getRateBps());
    }

    @Test
    void geofenceAndRotatingTokenValidation() {
        // Soft geofence check: Hazratganj Lucknow (26.8467, 80.9462)
        assertTrue(GeofenceAndQrUtils.evaluateSoftGeofence(26.8468, 80.9463, 26.8467, 80.9462, 150));
        assertFalse(GeofenceAndQrUtils.evaluateSoftGeofence(26.8600, 80.9600, 26.8467, 80.9462, 100));
        assertNull(GeofenceAndQrUtils.evaluateSoftGeofence(26.8467, 80.9462, null, null, null));

        // 60s rotating token
        long nowSec = Instant.parse("2026-04-15T10:00:30Z").getEpochSecond();
        String token = GeofenceAndQrUtils.generateRotatingToken("secret-123", nowSec);
        assertEquals(10, token.length());
        assertTrue(GeofenceAndQrUtils.isValidRotatingToken("secret-123", token, nowSec));
        // Expired 130 seconds later
        assertFalse(GeofenceAndQrUtils.isValidRotatingToken("secret-123", token, nowSec + 130));
    }
}
