package com.gymkhata.integration;

import com.gymkhata.admin.AdminService;
import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.auth.security.JwtAuthenticationFilter;
import com.gymkhata.billing.BillingController;
import com.gymkhata.billing.BillingService;
import com.gymkhata.billing.PaymentService;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.checkin.CheckinController;
import com.gymkhata.checkin.CheckinService;
import com.gymkhata.checkin.PublicAttendanceController;
import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.checkin.repo.CheckinRepository;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.CommissionRule;
import com.gymkhata.member.MemberController;
import com.gymkhata.member.MemberService;
import com.gymkhata.member.model.Member;
import com.gymkhata.membership.MembershipController;
import com.gymkhata.membership.MembershipService;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.messaging.model.OutboxMessage;
import com.gymkhata.plan.PlanService;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.pt.CommissionService;
import com.gymkhata.pt.PtController;
import com.gymkhata.pt.PtService;
import com.gymkhata.pt.model.CommissionEntry;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.model.PtPackage;
import com.gymkhata.pt.model.Trainer;
import com.gymkhata.report.ReportService;
import com.gymkhata.tenant.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AttendanceAndPtIntegrationTest {

    @Autowired private AdminService adminService;
    @Autowired private GymRepository gymRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantService tenantService;
    @Autowired private MemberService memberService;
    @Autowired private MemberController memberController;
    @Autowired private PlanService planService;
    @Autowired private MembershipService membershipService;
    @Autowired private MembershipController membershipController;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private BillingService billingService;
    @Autowired private BillingController billingController;
    @Autowired private PaymentService paymentService;
    @Autowired private CheckinService checkinService;
    @Autowired private CheckinController checkinController;
    @Autowired private CheckinRepository checkinRepository;
    @Autowired private PublicAttendanceController publicAttendanceController;
    @Autowired private PtService ptService;
    @Autowired private PtController ptController;
    @Autowired private CommissionService commissionService;
    @Autowired private ReportService reportService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User u) {
        var auth = new UsernamePasswordAuthenticationToken(
                u,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void attendanceDisabledByDefaultBlocksCheckinApisWhileCoreMembershipWorksAndFullQrSelfCheckinLifecycleSucceedsWhenEnabled() {
        String suffix = String.valueOf(System.nanoTime() % 100000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Self Checkin Gym " + suffix, "Owner One", "+91951" + String.format("%07d", Integer.parseInt(suffix)),
                "owner" + suffix + "@test.in", "Lucknow", "Uttar Pradesh", "09AABCU9603R1ZM", true,
                "Passw0rd!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        // 1. Verify attendanceEnabled is OFF by default
        Gym gym = tenantService.getGym();
        assertFalse(gym.getSettings().isAttendanceEnabled());

        // 2. Enrol a member while attendance is OFF -> membership, billing, and PIN generation work
        Member savedMember = memberService.create(
                "Rohan SelfCheck", "+91952" + String.format("%07d", Integer.parseInt(suffix)),
                null, "M", null, null, "Uttar Pradesh", true, null);
        String pin = savedMember.getGeneratedPin();
        assertNotNull(pin);
        assertEquals(4, pin.length());
        assertThat(savedMember.getWelcomeWhatsappText()).contains(pin);

        Plan monthly = planService.create("Monthly Gym", 30, 118000L, 20000L);
        Membership ms = membershipService.enrol(savedMember.getId(), monthly.getId(), 20000L, 10000L, null);
        Invoice msInv = billingService.generateInvoice(ms.getId());
        assertNotNull(msInv.getId());

        // 3. Attempting manual or QR check-in when attendanceEnabled=false returns 403 FEATURE_DISABLED
        BusinessException disabledManual = assertThrows(BusinessException.class,
                () -> checkinService.checkIn(savedMember.getId()));
        assertEquals("FEATURE_DISABLED", disabledManual.getCode());

        String slug = gymRepository.findById(gymId).orElseThrow().getSlug();
        String initialSlug = slug;
        BusinessException disabledQr = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(initialSlug,
                        new PublicAttendanceController.SelfCheckinRequest(savedMember.getPhone(), pin, null, null, null, null),
                        null, "TestAgent", new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertEquals("FEATURE_DISABLED", disabledQr.getCode());

        // 4. Owner enables attendance + geofence + selfCheckout
        Gym.Settings s = gym.getSettings();
        s.setAttendanceEnabled(true);
        s.setSelfCheckoutEnabled(true);
        s.setAutoCloseDurationMinutes(120);
        s.setGeofenceLat(26.8467);
        s.setGeofenceLng(80.9462);
        s.setGeofenceRadiusMeters(200);
        Gym updatedGym = tenantService.updateSettings(s);
        slug = updatedGym.getSlug();
        assertNotNull(slug);
        String activeSlug = slug;

        // Printable A4 QR Poster PDF works
        ResponseEntity<byte[]> posterRes = checkinController.posterPdf();
        assertEquals(200, posterRes.getStatusCode().value());
        assertThat(new String(posterRes.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        // 5. Brute-force PIN lockout: 5 wrong PIN attempts -> locked out with 429 PIN_LOCKED
        for (int attempt = 1; attempt <= 5; attempt++) {
            BusinessException badPin = assertThrows(BusinessException.class,
                    () -> publicAttendanceController.checkIn(activeSlug,
                            new PublicAttendanceController.SelfCheckinRequest(savedMember.getPhone(), "0000", null, null, null, null),
                            null, "TestAgent", new MockHttpServletRequest(), new MockHttpServletResponse()));
            if (attempt < 5) {
                assertEquals("INVALID_PIN", badPin.getCode());
            } else {
                assertEquals("PIN_LOCKED", badPin.getCode());
            }
        }
        // Even with the right PIN, locked out until owner resets PIN
        BusinessException stillLocked = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(activeSlug,
                        new PublicAttendanceController.SelfCheckinRequest(savedMember.getPhone(), pin, null, null, null, null),
                        null, "TestAgent", new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertEquals("PIN_LOCKED", stillLocked.getCode());

        // Owner resets PIN -> clears lockout and issues new 4-digit PIN
        Map<String, Object> resetRes = memberController.resetPin(savedMember.getId());
        String newPin = (String) resetRes.get("pin");
        assertNotNull(newPin);
        assertEquals(4, newPin.length());

        // 6. First time check-in with phone + newPin outside geofence (soft flag recorded, never blocks!)
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        Map<String, Object> check1 = publicAttendanceController.checkIn(
                activeSlug,
                new PublicAttendanceController.SelfCheckinRequest(savedMember.getPhone(), newPin, null, null, 28.6139, 77.2090),
                null, "Device1", new MockHttpServletRequest(), res1
        );
        assertEquals("GREEN", check1.get("badgeColor"));
        String deviceToken1 = (String) check1.get("deviceToken");
        assertNotNull(deviceToken1);
        assertEquals(false, check1.get("withinGeofence"));
        assertNotNull(res1.getHeader("Set-Cookie"));

        // 7. Second check-in on the same local day is rejected with 409 ALREADY_CHECKED_IN
        BusinessException sameDay = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(
                        activeSlug,
                        new PublicAttendanceController.SelfCheckinRequest(null, null, null, null, null, null),
                        deviceToken1, "Device1", requestWithDeviceCookie(deviceToken1), new MockHttpServletResponse()));
        assertEquals("ALREADY_CHECKED_IN", sameDay.getCode());

        // 8. Optional "I'm leaving" self-checkout works
        Checkin checkedOut = publicAttendanceController.checkOut(activeSlug, deviceToken1, requestWithDeviceCookie(deviceToken1));
        assertNotNull(checkedOut.getCheckOutAt());
        assertFalse(checkedOut.isAutoClosed());

        // 9. Test auto-close of stale open sessions after autoCloseDurationMinutes
        checkinRepository.deleteAll();
        Checkin staleOpen = new Checkin();
        staleOpen.setGymId(gymId);
        staleOpen.setMemberId(savedMember.getId());
        staleOpen.setMemberName(savedMember.getName());
        staleOpen.setDate(LocalDate.now());
        staleOpen.setMethod(Checkin.METHOD_QR_SELF);
        staleOpen.setCheckInAt(Instant.now().minus(180, ChronoUnit.MINUTES));
        staleOpen = checkinRepository.save(staleOpen);

        Map<String, Object> analytics = checkinController.analytics(14);
        assertEquals(1, ((Number) analytics.get("todayVisits")).intValue());
        Checkin reloadedStale = checkinRepository.findById(staleOpen.getId()).orElseThrow();
        assertNotNull(reloadedStale.getCheckOutAt());
        assertTrue(reloadedStale.isAutoClosed());

        // 10. Test Strict Rotating QR Mode + Device binding limit (max 2 devices) + Device revocation
        checkinRepository.deleteAll();
        s.setStrictQrMode(true);
        tenantService.updateSettings(s);

        // Invalid rotating token in strict mode -> 400 INVALID_QR_TOKEN
        BusinessException badQr = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(
                        activeSlug,
                        new PublicAttendanceController.SelfCheckinRequest(null, null, null, "badtoken", null, null),
                        deviceToken1, "Device1", requestWithDeviceCookie(deviceToken1), new MockHttpServletResponse()));
        assertEquals("INVALID_QR_TOKEN", badQr.getCode());

        // Valid rotating token from /screen -> one-tap check-in with bound device token succeeds!
        Map<String, Object> screenData = publicAttendanceController.screen(activeSlug);
        String validQrToken = (String) screenData.get("token");
        Map<String, Object> oneTap = publicAttendanceController.checkIn(
                activeSlug,
                new PublicAttendanceController.SelfCheckinRequest(null, null, null, validQrToken, 26.8467, 80.9462),
                deviceToken1, "Device1", requestWithDeviceCookie(deviceToken1), new MockHttpServletResponse());
        assertEquals("GREEN", oneTap.get("badgeColor"));
        assertEquals(true, oneTap.get("withinGeofence"));

        // Bind a 2nd device -> succeeds; 3rd device -> rejected with 400 MAX_DEVICES_REACHED
        checkinRepository.deleteAll();
        Map<String, Object> dev2 = publicAttendanceController.checkIn(
                activeSlug,
                new PublicAttendanceController.SelfCheckinRequest(savedMember.getPhone(), newPin, null, validQrToken, null, null),
                null, "Device2", new MockHttpServletRequest(), new MockHttpServletResponse());
        assertNotNull(dev2.get("deviceToken"));

        checkinRepository.deleteAll();
        BusinessException maxDev = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(
                        activeSlug,
                        new PublicAttendanceController.SelfCheckinRequest(savedMember.getPhone(), newPin, null, validQrToken, null, null),
                        null, "Device3", new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertEquals("MAX_DEVICES_REACHED", maxDev.getCode());

        // Owner revokes all devices -> old device token is no longer accepted without phone+PIN
        memberController.revokeAllDevices(savedMember.getId());
        BusinessException revokedEx = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(
                        activeSlug,
                        new PublicAttendanceController.SelfCheckinRequest(null, null, null, validQrToken, null, null),
                        deviceToken1, "Device1", requestWithDeviceCookie(deviceToken1), new MockHttpServletResponse()));
        assertEquals("AUTH_REQUIRED", revokedEx.getCode());

        // 11. Expired member check-in never blocks entry: returns RED badge + UPI pay link + gym contact
        Member expiredMember = memberService.create(
                "Expired Vikram", "+91953" + String.format("%07d", Integer.parseInt(suffix)),
                null, "M", null, null, "Uttar Pradesh", true, null);
        String expPin = expiredMember.getGeneratedPin();
        Membership expMs = membershipService.enrol(expiredMember.getId(), monthly.getId());
        expMs.setStartDate(LocalDate.now().minusDays(45));
        expMs.setExpiryDate(LocalDate.now().minusDays(15));
        expMs.setStatus(Membership.STATUS_EXPIRED);
        membershipRepository.save(expMs);
        billingService.generateInvoice(expMs.getId());

        Map<String, Object> expResult = publicAttendanceController.checkIn(
                activeSlug,
                new PublicAttendanceController.SelfCheckinRequest(expiredMember.getPhone(), expPin, null, validQrToken, null, null),
                null, "ExpDevice", new MockHttpServletRequest(), new MockHttpServletResponse());
        assertEquals("RED", expResult.get("badgeColor"));
        assertNotNull(expResult.get("payLink"));
        assertNotNull(expResult.get("gymPhone"));

        // 12. "Not seen in N days" list & 1-click bilingual WhatsApp nudge
        Member unseenMember = memberService.create(
                "Unseen Pooja", "+91954" + String.format("%07d", Integer.parseInt(suffix)),
                null, "F", null, null, "Uttar Pradesh", true, null);
        membershipService.enrol(unseenMember.getId(), monthly.getId());

        OutboxMessage nudgeMsg = checkinController.nudge(unseenMember.getId());
        assertNotNull(nudgeMsg.getId());
        assertThat(nudgeMsg.getWaLink()).contains("wa.me");
    }

    @Test
    void personalTrainingPartialPaymentAccrualsRuleImmutabilityCreditNoteReversalAndStatements() {
        String suffix = String.valueOf(System.nanoTime() % 100000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "PT Commission Gym " + suffix, "Owner PT", "+91961" + String.format("%07d", Integer.parseInt(suffix)),
                "pt" + suffix + "@test.in", "Indore", "Madhya Pradesh", "23AAACI5678G1Z2", true,
                "Passw0rd!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        // 1. Create Trainer with 20% (2000 bps) commission on taxable value
        Trainer trainer = ptController.createTrainer(new PtController.TrainerRequest(
                "Coach Kabir", "+91962" + String.format("%07d", Integer.parseInt(suffix)),
                true, CommissionRule.percentOfTaxable(2000)));
        assertNotNull(trainer.getId());

        // 2. Create Session-based PT Package: 10 sessions, 60 days, ₹10,500 inclusive (₹10,000 taxable + ₹500 GST @ 5%)
        PtPackage pkg = ptController.createPackage(new PtController.PtPackageRequest(
                "10-Session Strength PT", PtPackage.TYPE_SESSION_BASED, 60, 10, 1_050_000L, "999723", null));

        Member member = memberService.create(
                "Aarav PT Client", "+91963" + String.format("%07d", Integer.parseInt(suffix)),
                null, "M", null, null, "Madhya Pradesh", true, null);

        // 3. Sell PT package -> generates GST invoice with SAC 999723
        PtEnrolment enrolment = ptController.sellPt(new PtController.SellPtRequest(
                member.getId(), pkg.getId(), trainer.getId(), 0L));
        Invoice invoice = billingService.get(enrolment.getInvoiceId());
        assertEquals(1_050_000L, invoice.getTotalPaise());
        assertEquals(1_000_000L, invoice.getLines().get(0).getTaxablePaise());

        // 4. Record 60% partial payment (₹6,300 = 630,000 paise) -> 60% of ₹2,000 commission = ₹1,200 (120,000 paise) accrued
        paymentService.recordCounterPayment(invoice.getId(), 630_000L, Payment.METHOD_CASH);
        String month = YearMonth.now().toString();
        Map<String, Object> stmtAfter60 = ptController.statement(trainer.getId(), month);
        assertEquals(120_000L, ((Number) stmtAfter60.get("accruedPaise")).longValue());
        assertEquals(120_000L, ((Number) stmtAfter60.get("balanceDuePaise")).longValue());

        // 5. Change Trainer's commission rule from 20% to 10% -> past ledger entries and snapshotted enrolment rule MUST NOT change
        ptController.updateTrainer(trainer.getId(), new PtController.TrainerUpdateRequest(
                trainer.getName(), trainer.getPhone(), true, CommissionRule.percentOfTaxable(1000)));
        Map<String, Object> stmtAfterRuleChange = ptController.statement(trainer.getId(), month);
        assertEquals(120_000L, ((Number) stmtAfterRuleChange.get("accruedPaise")).longValue());
        @SuppressWarnings("unchecked")
        List<CommissionEntry> entriesAfter60 = (List<CommissionEntry>) stmtAfterRuleChange.get("entries");
        assertEquals(2000, entriesAfter60.get(0).getRuleSnapshot().getRateBps());

        // 6. Record remaining 40% payment (₹4,200 = 420,000 paise) -> accrues remaining ₹800 (80,000 paise) using snapshotted 20% rule!
        paymentService.recordCounterPayment(invoice.getId(), 420_000L, Payment.METHOD_CARD);
        Map<String, Object> stmtAfter100 = ptController.statement(trainer.getId(), month);
        assertEquals(200_000L, ((Number) stmtAfter100.get("accruedPaise")).longValue());

        // 7. Decrement PT session manually from member's page
        PtEnrolment afterSession = ptController.markSessionDone(enrolment.getId());
        assertEquals(1, afterSession.getDeliveredSessions());
        assertEquals(9, afterSession.getRemainingSessions());

        // 8. Issue Credit Note on the PT invoice -> creates automatic reversing entry in commission ledger!
        Invoice cn = billingController.creditNote(invoice.getId(), new BillingController.CreditNoteRequest("Client moved city"));
        assertEquals(Invoice.TYPE_CREDIT_NOTE, cn.getType());

        Map<String, Object> stmtAfterCn = ptController.statement(trainer.getId(), month);
        assertEquals(-200_000L, ((Number) stmtAfterCn.get("reversalsPaise")).longValue());
        assertEquals(0L, ((Number) stmtAfterCn.get("netPayablePaise")).longValue());

        // 9. Manual adjustment (+₹500 bonus) and Payout (₹300 paid via UPI) + CSV & PDF exports
        ptController.addAdjustment(new PtController.AdjustmentRequest(trainer.getId(), month, 50_000L, "Star trainer bonus"));
        ptController.markPayoutPaid(new PtController.PayoutRequest(trainer.getId(), month, 30_000L, "UPI", LocalDate.now(), "Part payout"));

        Map<String, Object> finalStmt = ptController.statement(trainer.getId(), month);
        assertEquals(50_000L, ((Number) finalStmt.get("adjustmentsPaise")).longValue());
        assertEquals(30_000L, ((Number) finalStmt.get("paidPaise")).longValue());
        assertEquals(20_000L, ((Number) finalStmt.get("balanceDuePaise")).longValue());
        assertThat((String) finalStmt.get("whatsappShareText")).contains("Coach Kabir");

        ResponseEntity<byte[]> csvRes = ptController.exportCsv(trainer.getId(), month);
        assertEquals(200, csvRes.getStatusCode().value());
        String csvText = new String(csvRes.getBody(), StandardCharsets.UTF_8);
        assertThat(csvText).contains("ACCRUAL").contains("REVERSAL").contains("ADJUSTMENT").contains("PAYOUT");

        ResponseEntity<byte[]> pdfRes = ptController.exportPdf(trainer.getId(), month);
        assertEquals(200, pdfRes.getStatusCode().value());
        assertThat(new String(pdfRes.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        // 10. Verify PT Reports & Tenant Isolation against a second gym
        assertThat(reportService.ptRevenueByMonth()).isNotEmpty();
        assertThat(reportService.trainerCommissions(month)).isNotEmpty();
        assertThat(reportService.trainerCollections()).isNotEmpty();

        Map<String, Object> otherGym = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Other Gym " + suffix, "Owner Two", "+91964" + String.format("%07d", Integer.parseInt(suffix)),
                "other" + suffix + "@test.in", "Pune", "Maharashtra", null, false,
                "Passw0rd!23", LocalDate.now().plusMonths(6)));
        User owner2 = userRepository.findByGymIdAndRole((String) otherGym.get("gymId"), User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner2);

        assertThat(ptController.listTrainers(null)).isEmpty();
        assertThrows(BusinessException.class, () -> ptController.statement(trainer.getId(), month));
        assertThrows(BusinessException.class, () -> ptController.markSessionDone(enrolment.getId()));
    }

    @Test
    void membershipPlansLateRenewalPolicyFreezeExtensionJoiningFeeDiscountsAndDuesList() {
        String suffix = String.valueOf(System.nanoTime() % 100000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Membership Rules Gym " + suffix, "Owner Mem", "+91971" + String.format("%07d", Integer.parseInt(suffix)),
                "mem" + suffix + "@test.in", "Jaipur", "Rajasthan", null, false,
                "Passw0rd!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        // 1. Create Monthly (30d), Quarterly (90d), Half-Yearly (180d), Yearly (365d), and Custom (45d) plans with joining fee
        Plan monthly = planService.create("Monthly", 30, 150_000L, 50_000L);
        Plan quarterly = planService.create("Quarterly", 90, 400_000L, 0L);
        Plan halfYearly = planService.create("Half-Yearly", 180, 750_000L, 0L);
        Plan yearly = planService.create("Yearly", 365, 1_200_000L, 0L);
        Plan custom45 = planService.create("Custom 45 Days", 45, 200_000L, 0L);
        assertEquals(5, planService.list().size());

        Member member = memberService.create(
                "Priya Renewal", "+91972" + String.format("%07d", Integer.parseInt(suffix)),
                null, "F", null, null, "Rajasthan", true, null);

        // 2. Enrol with joining fee (₹500) and discount (₹200) -> ₹1,500 + ₹500 - ₹200 = ₹1,800 (180,000 paise)
        Membership m1 = membershipService.enrol(member.getId(), monthly.getId(), 50_000L, 20_000L, null);
        Invoice inv1 = billingService.generateInvoice(m1.getId());
        assertEquals(180_000L, inv1.getTotalPaise());
        assertEquals(20_000L, inv1.getDiscountPaise());
        assertEquals(50_000L, inv1.getJoiningFeePaise());

        // Pay ₹1,000 partially -> ₹800 remains on Dues list
        paymentService.recordCounterPayment(inv1.getId(), 100_000L, Payment.METHOD_CASH);
        List<Map<String, Object>> dues = billingController.dues();
        assertThat(dues).hasSize(1);
        assertEquals(80_000L, ((Number) dues.get(0).get("balancePaise")).longValue());
        assertEquals("PARTIAL", dues.get(0).get("status"));

        // 3. Simulate m1 expired 10 days ago and set lateRenewalPolicy = FROM_EXPIRY -> renewal starts from oldExpiry + 1 day!
        LocalDate oldExpiry = LocalDate.now().minusDays(10);
        m1.setStartDate(oldExpiry.minusDays(29));
        m1.setExpiryDate(oldExpiry);
        m1.setStatus(Membership.STATUS_EXPIRED);
        membershipRepository.save(m1);

        Gym gym = tenantService.getGym();
        Gym.Settings s = gym.getSettings();
        s.setLateRenewalPolicy(Gym.Settings.RENEWAL_FROM_EXPIRY);
        tenantService.updateSettings(s);

        Membership m2 = membershipService.enrol(member.getId(), quarterly.getId(), 0L, 0L, null);
        assertEquals(oldExpiry.plusDays(1), m2.getStartDate());

        // 4. Freeze / Pause membership and unfreeze with extension
        LocalDate expiryBeforeFreeze = m2.getExpiryDate();
        Membership frozen = membershipController.freeze(m2.getId(), new MembershipController.FreezeRequest(null, "Out of station"));
        assertEquals(Membership.STATUS_FROZEN, frozen.getStatus());
        assertNotNull(frozen.getFrozenAt());

        // Simulate frozen 5 days ago
        frozen.setFrozenAt(LocalDate.now().minusDays(5));
        membershipRepository.save(frozen);
        Membership unfrozen = membershipController.unfreeze(frozen.getId(), null);
        assertEquals(Membership.STATUS_ACTIVE, unfrozen.getStatus());
        assertEquals(5, unfrozen.getTotalFrozenDays());
        assertEquals(expiryBeforeFreeze.plusDays(5), unfrozen.getExpiryDate());
    }

    private MockHttpServletRequest requestWithDeviceCookie(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new jakarta.servlet.http.Cookie("gk_device_token", token));
        return req;
    }
}
