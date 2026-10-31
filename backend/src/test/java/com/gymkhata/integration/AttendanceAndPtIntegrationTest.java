package com.gymkhata.integration;

import com.gymkhata.admin.AdminService;
import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
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
import com.gymkhata.pt.PtController;
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
    @Autowired private PtController ptController;
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

    private String createGymAndLogin(String labelPrefix) {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 90000) + 10000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                labelPrefix + " " + suffix, "Owner " + suffix, "+9195" + String.format("%08d", Integer.parseInt(suffix)),
                "owner" + suffix + "@test.in", "Lucknow", "Uttar Pradesh", "09AABCU9603R1ZM", true,
                "Passw0rd!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);
        return gymId;
    }

    @Test
    void attendanceDisabledByDefaultBlocksManualAndQrCheckinApisWhileCoreMembershipWorks() {
        String gymId = createGymAndLogin("AttOffGym");
        Gym gym = tenantService.getGym();
        assertFalse(gym.getSettings().isAttendanceEnabled());

        Member member = memberService.create(
                "Rohan SelfCheck", "+91952" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                null, "M", null, null, "Uttar Pradesh", true, null);
        String pin = member.getGeneratedPin();
        assertNotNull(pin);
        assertEquals(4, pin.length());

        Plan monthly = planService.create("Monthly Gym", 30, 118000L, 20000L);
        Membership ms = membershipService.enrol(member.getId(), monthly.getId(), 20000L, 10000L, null);
        Invoice msInv = billingService.generateInvoice(ms.getId());
        assertNotNull(msInv.getId());

        BusinessException disabledManual = assertThrows(BusinessException.class,
                () -> checkinService.checkIn(member.getId()));
        assertEquals("FEATURE_DISABLED", disabledManual.getCode());

        String slug = gymRepository.findById(gymId).orElseThrow().getSlug();
        BusinessException disabledQr = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(slug,
                        new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), pin, null, null, null, null),
                        null, "TestAgent", new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertEquals("FEATURE_DISABLED", disabledQr.getCode());
    }

    @Test
    void qrSelfCheckinWithSoftGeofenceSameDayDeduplicationSelfCheckoutAndAutoClose() {
        String gymId = createGymAndLogin("QrSelfGym");
        Gym gym = tenantService.getGym();
        Gym.Settings s = gym.getSettings();
        s.setAttendanceEnabled(true);
        s.setSelfCheckoutEnabled(true);
        s.setAutoCloseDurationMinutes(120);
        s.setGeofenceLat(26.8467);
        s.setGeofenceLng(80.9462);
        s.setGeofenceRadiusMeters(200);
        String slug = tenantService.updateSettings(s).getSlug();

        ResponseEntity<byte[]> posterRes = checkinController.posterPdf();
        assertEquals(200, posterRes.getStatusCode().value());
        assertThat(new String(posterRes.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        Member member = memberService.create(
                "Geofence Member", "+91953" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                null, "M", null, null, "Uttar Pradesh", true, null);
        Plan monthly = planService.create("Monthly Gym", 30, 118000L);
        membershipService.enrol(member.getId(), monthly.getId());

        MockHttpServletResponse res1 = new MockHttpServletResponse();
        Map<String, Object> check1 = publicAttendanceController.checkIn(
                slug,
                new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), member.getGeneratedPin(), null, null, 28.6139, 77.2090),
                null, "Device1", new MockHttpServletRequest(), res1);
        assertEquals("GREEN", check1.get("badgeColor"));
        assertEquals(false, check1.get("withinGeofence"));
        String deviceToken1 = (String) check1.get("deviceToken");
        assertNotNull(deviceToken1);

        BusinessException sameDay = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(
                        slug,
                        new PublicAttendanceController.SelfCheckinRequest(null, null, null, null, null, null),
                        deviceToken1, "Device1", requestWithDeviceCookie(deviceToken1), new MockHttpServletResponse()));
        assertEquals("ALREADY_CHECKED_IN", sameDay.getCode());

        Checkin checkedOut = publicAttendanceController.checkOut(slug, deviceToken1, requestWithDeviceCookie(deviceToken1));
        assertNotNull(checkedOut.getCheckOutAt());
        assertFalse(checkedOut.isAutoClosed());

        checkinRepository.deleteAll();
        Checkin staleOpen = new Checkin();
        staleOpen.setGymId(gymId);
        staleOpen.setMemberId(member.getId());
        staleOpen.setMemberName(member.getName());
        staleOpen.setDate(LocalDate.now());
        staleOpen.setMethod(Checkin.METHOD_QR_SELF);
        staleOpen.setCheckInAt(Instant.now().minus(180, ChronoUnit.MINUTES));
        staleOpen = checkinRepository.save(staleOpen);

        Map<String, Object> analytics = checkinController.analytics(14);
        assertEquals(1, ((Number) analytics.get("todayVisits")).intValue());
        Checkin reloadedStale = checkinRepository.findById(staleOpen.getId()).orElseThrow();
        assertNotNull(reloadedStale.getCheckOutAt());
        assertTrue(reloadedStale.isAutoClosed());
    }

    @Test
    void strictQrModeDeviceBindingLimitOfTwoAndOwnerDeviceRevocation() {
        createGymAndLogin("StrictQrGym");
        Gym gym = tenantService.getGym();
        Gym.Settings s = gym.getSettings();
        s.setAttendanceEnabled(true);
        s.setStrictQrMode(true);
        String slug = tenantService.updateSettings(s).getSlug();

        Member member = memberService.create(
                "Strict Member", "+91954" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                null, "M", null, null, "Uttar Pradesh", true, null);
        String pin = member.getGeneratedPin();
        Plan monthly = planService.create("Monthly", 30, 100_000L);
        membershipService.enrol(member.getId(), monthly.getId());

        BusinessException badQr = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(
                        slug,
                        new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), pin, null, "badtoken", null, null),
                        null, "Device1", new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertEquals("INVALID_QR_TOKEN", badQr.getCode());

        String validQrToken = (String) publicAttendanceController.screen(slug).get("token");
        Map<String, Object> dev1 = publicAttendanceController.checkIn(
                slug,
                new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), pin, null, validQrToken, null, null),
                null, "Device1", new MockHttpServletRequest(), new MockHttpServletResponse());
        String deviceToken1 = (String) dev1.get("deviceToken");
        assertNotNull(deviceToken1);

        checkinRepository.deleteAll();
        Map<String, Object> dev2 = publicAttendanceController.checkIn(
                slug,
                new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), pin, null, validQrToken, null, null),
                null, "Device2", new MockHttpServletRequest(), new MockHttpServletResponse());
        assertNotNull(dev2.get("deviceToken"));

        checkinRepository.deleteAll();
        BusinessException maxDev = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(
                        slug,
                        new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), pin, null, validQrToken, null, null),
                        null, "Device3", new MockHttpServletRequest(), new MockHttpServletResponse()));
        assertEquals("MAX_DEVICES_REACHED", maxDev.getCode());

        memberController.revokeAllDevices(member.getId());
        BusinessException revokedEx = assertThrows(BusinessException.class,
                () -> publicAttendanceController.checkIn(
                        slug,
                        new PublicAttendanceController.SelfCheckinRequest(null, null, null, validQrToken, null, null),
                        deviceToken1, "Device1", requestWithDeviceCookie(deviceToken1), new MockHttpServletResponse()));
        assertEquals("AUTH_REQUIRED", revokedEx.getCode());
    }

    @Test
    void expiredMemberCheckinReturnsRedBadgeWithUpiPayLinkAndNotSeenNudgeWorks() {
        createGymAndLogin("ExpiredBadgeGym");
        Gym gym = tenantService.getGym();
        gym.getSettings().setAttendanceEnabled(true);
        String slug = tenantService.updateSettings(gym.getSettings()).getSlug();

        Plan monthly = planService.create("Monthly", 30, 118_000L);
        Member expiredMember = memberService.create(
                "Expired Vikram", "+91955" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                null, "M", null, null, "Uttar Pradesh", true, null);
        Membership expMs = membershipService.enrol(expiredMember.getId(), monthly.getId());
        expMs.setStartDate(LocalDate.now().minusDays(45));
        expMs.setExpiryDate(LocalDate.now().minusDays(15));
        expMs.setStatus(Membership.STATUS_EXPIRED);
        membershipRepository.save(expMs);
        billingService.generateInvoice(expMs.getId());

        Map<String, Object> expResult = publicAttendanceController.checkIn(
                slug,
                new PublicAttendanceController.SelfCheckinRequest(expiredMember.getPhone(), expiredMember.getGeneratedPin(), null, null, null, null),
                null, "ExpDevice", new MockHttpServletRequest(), new MockHttpServletResponse());
        assertEquals("RED", expResult.get("badgeColor"));
        assertNotNull(expResult.get("payLink"));
        assertNotNull(expResult.get("gymPhone"));

        OutboxMessage nudgeMsg = checkinController.nudge(expiredMember.getId());
        assertNotNull(nudgeMsg.getId());
        assertThat(nudgeMsg.getWaLink()).contains("wa.me");
    }

    @Test
    void personalTrainingProportionalPaymentAccrualsAndRuleSnapshotImmutability() {
        createGymAndLogin("PtAccrualGym");

        Trainer trainer = ptController.createTrainer(new PtController.TrainerRequest(
                "Coach Kabir", "+91962" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                true, CommissionRule.percentOfTaxable(2000)));
        PtPackage pkg = ptController.createPackage(new PtController.PtPackageRequest(
                "10-Session Strength PT", PtPackage.TYPE_SESSION_BASED, 60, 10, 1_050_000L, "999723", null));
        Member member = memberService.create(
                "Aarav PT Client", "+91963" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                null, "M", null, null, "Uttar Pradesh", true, null);

        PtEnrolment enrolment = ptController.sellPt(new PtController.SellPtRequest(
                member.getId(), pkg.getId(), trainer.getId(), 0L));
        Invoice invoice = billingService.get(enrolment.getInvoiceId());
        assertEquals(1_050_000L, invoice.getTotalPaise());
        assertEquals(1_000_000L, invoice.getLines().get(0).getTaxablePaise());

        paymentService.recordCounterPayment(invoice.getId(), 630_000L, Payment.METHOD_CASH);
        String month = YearMonth.now().toString();
        Map<String, Object> stmtAfter60 = ptController.statement(trainer.getId(), month);
        assertEquals(120_000L, ((Number) stmtAfter60.get("accruedPaise")).longValue());

        ptController.updateTrainer(trainer.getId(), new PtController.TrainerUpdateRequest(
                trainer.getName(), trainer.getPhone(), true, CommissionRule.percentOfTaxable(1000)));
        Map<String, Object> stmtAfterRuleChange = ptController.statement(trainer.getId(), month);
        assertEquals(120_000L, ((Number) stmtAfterRuleChange.get("accruedPaise")).longValue());
        @SuppressWarnings("unchecked")
        List<CommissionEntry> entriesAfter60 = (List<CommissionEntry>) stmtAfterRuleChange.get("entries");
        assertEquals(2000, entriesAfter60.get(0).getRuleSnapshot().getRateBps());

        paymentService.recordCounterPayment(invoice.getId(), 420_000L, Payment.METHOD_CARD);
        Map<String, Object> stmtAfter100 = ptController.statement(trainer.getId(), month);
        assertEquals(200_000L, ((Number) stmtAfter100.get("accruedPaise")).longValue());
    }

    @Test
    void personalTrainingCreditNoteReversalAdjustmentsPayoutsExportsAndTenantIsolation() {
        createGymAndLogin("PtReversalGym");

        Trainer trainer = ptController.createTrainer(new PtController.TrainerRequest(
                "Coach Reversal", "+91964" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                true, CommissionRule.percentOfTaxable(2000)));
        PtPackage pkg = ptController.createPackage(new PtController.PtPackageRequest(
                "10-Session PT", PtPackage.TYPE_SESSION_BASED, 60, 10, 1_050_000L, "999723", null));
        Member member = memberService.create(
                "Reversal Client", "+91965" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                null, "M", null, null, "Uttar Pradesh", true, null);

        PtEnrolment enrolment = ptController.sellPt(new PtController.SellPtRequest(
                member.getId(), pkg.getId(), trainer.getId(), 0L));
        paymentService.recordCounterPayment(enrolment.getInvoiceId(), 1_050_000L, Payment.METHOD_CASH);

        PtEnrolment afterSession = ptController.markSessionDone(enrolment.getId());
        assertEquals(1, afterSession.getDeliveredSessions());
        assertEquals(9, afterSession.getRemainingSessions());

        String month = YearMonth.now().toString();
        Invoice cn = billingController.creditNote(enrolment.getInvoiceId(), new BillingController.CreditNoteRequest("Client moved city"));
        assertEquals(Invoice.TYPE_CREDIT_NOTE, cn.getType());

        Map<String, Object> stmtAfterCn = ptController.statement(trainer.getId(), month);
        assertEquals(-200_000L, ((Number) stmtAfterCn.get("reversalsPaise")).longValue());
        assertEquals(0L, ((Number) stmtAfterCn.get("netPayablePaise")).longValue());

        ptController.addAdjustment(new PtController.AdjustmentRequest(trainer.getId(), month, 50_000L, "Star trainer bonus"));
        ptController.markPayoutPaid(new PtController.PayoutRequest(trainer.getId(), month, 30_000L, "UPI", LocalDate.now(), "Part payout"));

        Map<String, Object> finalStmt = ptController.statement(trainer.getId(), month);
        assertEquals(50_000L, ((Number) finalStmt.get("adjustmentsPaise")).longValue());
        assertEquals(30_000L, ((Number) finalStmt.get("paidPaise")).longValue());
        assertEquals(20_000L, ((Number) finalStmt.get("balanceDuePaise")).longValue());

        ResponseEntity<byte[]> csvRes = ptController.exportCsv(trainer.getId(), month);
        assertThat(new String(csvRes.getBody(), StandardCharsets.UTF_8))
                .contains("ACCRUAL").contains("REVERSAL").contains("ADJUSTMENT").contains("PAYOUT");

        ResponseEntity<byte[]> pdfRes = ptController.exportPdf(trainer.getId(), month);
        assertThat(new String(pdfRes.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        assertThat(reportService.ptRevenueByMonth()).isNotEmpty();
        assertThat(reportService.trainerCommissions(month)).isNotEmpty();
        assertThat(reportService.trainerCollections()).isNotEmpty();

        createGymAndLogin("OtherTenantGym");
        assertThat(ptController.listTrainers(null)).isEmpty();
        assertThrows(BusinessException.class, () -> ptController.statement(trainer.getId(), month));
        assertThrows(BusinessException.class, () -> ptController.markSessionDone(enrolment.getId()));
    }

    @Test
    void membershipPlansJoiningFeeDiscountPartialPaymentDuesAndLateRenewalFromExpiry() {
        createGymAndLogin("MembershipRulesGym");

        Plan monthly = planService.create("Monthly", 30, 150_000L, 50_000L);
        Plan quarterly = planService.create("Quarterly", 90, 400_000L, 0L);
        planService.create("Half-Yearly", 180, 750_000L, 0L);
        planService.create("Yearly", 365, 1_200_000L, 0L);
        planService.create("Custom 45 Days", 45, 200_000L, 0L);
        assertEquals(5, planService.list().size());

        Member member = memberService.create(
                "Priya Renewal", "+91972" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                null, "F", null, null, "Uttar Pradesh", true, null);

        Membership m1 = membershipService.enrol(member.getId(), monthly.getId(), 50_000L, 20_000L, null);
        Invoice inv1 = billingService.generateInvoice(m1.getId());
        assertEquals(180_000L, inv1.getTotalPaise());
        assertEquals(20_000L, inv1.getDiscountPaise());
        assertEquals(50_000L, inv1.getJoiningFeePaise());

        paymentService.recordCounterPayment(inv1.getId(), 100_000L, Payment.METHOD_CASH);
        List<Map<String, Object>> dues = billingController.dues();
        assertThat(dues).hasSize(1);
        assertEquals(80_000L, ((Number) dues.get(0).get("balancePaise")).longValue());
        assertEquals("PARTIAL", dues.get(0).get("status"));

        LocalDate oldExpiry = LocalDate.now().minusDays(10);
        m1.setStartDate(oldExpiry.minusDays(29));
        m1.setExpiryDate(oldExpiry);
        m1.setStatus(Membership.STATUS_EXPIRED);
        membershipRepository.save(m1);

        Gym gym = tenantService.getGym();
        gym.getSettings().setLateRenewalPolicy(Gym.Settings.RENEWAL_FROM_EXPIRY);
        tenantService.updateSettings(gym.getSettings());

        Membership m2 = membershipService.enrol(member.getId(), quarterly.getId(), 0L, 0L, null);
        assertEquals(oldExpiry.plusDays(1), m2.getStartDate());
    }

    @Test
    void membershipFreezeAndUnfreezeExtendsExpiryByFrozenDays() {
        createGymAndLogin("FreezeGym");

        Plan quarterly = planService.create("Quarterly", 90, 400_000L, 0L);
        Member member = memberService.create(
                "Freeze Member", "+91973" + String.format("%07d", (int) (System.nanoTime() % 9000000 + 1000000)),
                null, "M", null, null, "Uttar Pradesh", true, null);

        Membership m = membershipService.enrol(member.getId(), quarterly.getId(), 0L, 0L, null);
        LocalDate expiryBeforeFreeze = m.getExpiryDate();

        Membership frozen = membershipController.freeze(m.getId(), new MembershipController.FreezeRequest(null, "Out of station"));
        assertEquals(Membership.STATUS_FROZEN, frozen.getStatus());
        assertNotNull(frozen.getFrozenAt());

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
