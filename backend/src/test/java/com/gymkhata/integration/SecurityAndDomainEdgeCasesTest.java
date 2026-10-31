package com.gymkhata.integration;

import com.gymkhata.admin.AdminController;
import com.gymkhata.admin.AdminService;
import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.AuthController;
import com.gymkhata.auth.AuthService;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.billing.BillingController;
import com.gymkhata.billing.BillingService;
import com.gymkhata.billing.GstConfig;
import com.gymkhata.billing.PaymentService;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.checkin.CheckinController;
import com.gymkhata.checkin.CheckinService;
import com.gymkhata.checkin.PublicAttendanceController;
import com.gymkhata.checkin.repo.CheckinRepository;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.CommissionCalculator;
import com.gymkhata.domain.CommissionRule;
import com.gymkhata.member.MemberController;
import com.gymkhata.member.MemberService;
import com.gymkhata.member.model.Member;
import com.gymkhata.membership.MembershipService;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.messaging.model.OutboxMessage;
import com.gymkhata.messaging.repo.OutboxRepository;
import com.gymkhata.plan.PlanService;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.pt.CommissionService;
import com.gymkhata.pt.PtController;
import com.gymkhata.pt.PtService;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.model.PtPackage;
import com.gymkhata.pt.model.Trainer;
import com.gymkhata.tenant.TenantService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SecurityAndDomainEdgeCasesTest {

    @Autowired private AdminService adminService;
    @Autowired private AdminController adminController;
    @Autowired private AuthController authController;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private GymRepository gymRepository;
    @Autowired private TenantService tenantService;
    @Autowired private MemberService memberService;
    @Autowired private MemberController memberController;
    @Autowired private PlanService planService;
    @Autowired private MembershipService membershipService;
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
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private com.gymkhata.member.repo.MemberRepository memberRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User u) {
        var auth = new UsernamePasswordAuthenticationToken(
                u,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private MockHttpServletRequest requestFromIp(String ip, String cookieToken) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.128.0.5");
        req.addHeader("X-Forwarded-For", ip);
        if (cookieToken != null) {
            req.setCookies(new jakarta.servlet.http.Cookie("gk_device_token", cookieToken));
        }
        return req;
    }

    @Test
    void passwordResetTokenNeverReturnedInPublicApiResponse() {
        String suffix = String.valueOf(System.nanoTime() % 100000);
        String phone = "+91981" + String.format("%07d", Integer.parseInt(suffix));
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Reset Security Gym " + suffix, "Reset Owner", phone,
                "reset" + suffix + "@test.in", "Delhi", "Delhi", null, false,
                "StrongPass!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");

        Map<String, String> publicRes = authController.requestReset(new AuthController.ResetRequest(phone));
        assertNotNull(publicRes);
        assertEquals("reset_requested", publicRes.get("status"));
        assertNull(publicRes.get("resetToken"));
        assertNull(publicRes.get("token"));
        assertThat(publicRes.toString().toLowerCase()).doesNotContain("uuid");

        User superAdmin = new User();
        superAdmin.setId("sa-test");
        superAdmin.setRole(User.ROLE_SUPER_ADMIN);
        superAdmin.setName("Super Admin");
        loginAs(superAdmin);

        Map<String, Object> adminRes = adminController.triggerReset(gymId);
        assertNotNull(adminRes);
        assertEquals("reset_token_issued", adminRes.get("status"));
        String adminToken = (String) adminRes.get("resetToken");
        assertNotNull(adminToken);
        assertFalse(adminToken.isBlank());
    }

    @Test
    void pinLockoutPerPhoneAndPerIpWithAlertNotification() {
        assertEquals(60L, CheckinService.calculateLockoutSeconds(1));
        assertEquals(300L, CheckinService.calculateLockoutSeconds(2));
        assertEquals(900L, CheckinService.calculateLockoutSeconds(3));
        assertEquals(3600L, CheckinService.calculateLockoutSeconds(4));

        String suffix = String.valueOf(System.nanoTime() % 100000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Lockout Gym " + suffix, "Lockout Owner", "+91982" + String.format("%07d", Integer.parseInt(suffix)),
                "lock" + suffix + "@test.in", "Mumbai", "Maharashtra", null, false,
                "StrongPass!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        Gym gym = tenantService.getGym();
        gym.getSettings().setAttendanceEnabled(true);
        tenantService.updateSettings(gym.getSettings());
        String slug = gym.getSlug();

        Member member = memberService.create(
                "Lockout Target", "+91983" + String.format("%07d", Integer.parseInt(suffix)),
                null, "M", null, null, "Maharashtra", true, null);
        String realPin = member.getGeneratedPin();

        String attackerIp = "198.51.100." + (Integer.parseInt(suffix) % 250 + 1);
        for (int i = 1; i <= 5; i++) {
            final int attempt = i;
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("10.128.0.5");
            req.addHeader("X-Forwarded-For", "1.1.1.1, " + attackerIp);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    publicAttendanceController.checkIn(slug,
                            new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), "9999", null, null, null, null),
                            null, "AttackerBot", req, new MockHttpServletResponse()));
            if (attempt < 5) {
                assertEquals("INVALID_PIN", ex.getCode());
            } else {
                assertEquals("PIN_LOCKED", ex.getCode());
            }
        }

        MockHttpServletRequest spoofReq = new MockHttpServletRequest();
        spoofReq.setRemoteAddr(attackerIp);
        spoofReq.addHeader("X-Forwarded-For", "8.8.8.8");
        BusinessException spoofEx = assertThrows(BusinessException.class, () ->
                publicAttendanceController.checkIn(slug,
                        new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), realPin, null, null, null, null),
                        null, "AttackerSpoof", spoofReq, new MockHttpServletResponse()));
        assertEquals("PIN_LOCKED", spoofEx.getCode());

        List<OutboxMessage> alerts = outboxRepository.findByGymIdOrderByCreatedAtDesc(gymId);
        assertThat(alerts).anyMatch(m -> m.getVariables() != null
                && String.valueOf(m.getVariables().get("alert")).contains("Multiple failed PIN attempts"));

        Member member2 = memberService.create(
                "Other Member", "+91984" + String.format("%07d", Integer.parseInt(suffix)),
                null, "F", null, null, "Maharashtra", true, null);
        String mem2Pin = member2.getGeneratedPin();

        MockHttpServletRequest reqOtherMem = requestFromIp(attackerIp, null);
        Map<String, Object> mem2Res = publicAttendanceController.checkIn(slug,
                new PublicAttendanceController.SelfCheckinRequest(member2.getPhone(), mem2Pin, null, null, null, null),
                null, "Browser", reqOtherMem, new MockHttpServletResponse());
        assertNotNull(mem2Res);
        assertEquals(member2.getId(), mem2Res.get("memberId"));

        memberController.resetPinLockout(member.getId());

        MockHttpServletRequest unlockedReq = requestFromIp(attackerIp, null);
        Map<String, Object> success = publicAttendanceController.checkIn(slug,
                new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), realPin, null, null, null, null),
                null, "LegitPhone", unlockedReq, new MockHttpServletResponse());
        assertNotNull(success);
        assertEquals("RED", success.get("badgeColor"));
        assertNotNull(success.get("payLink"));
    }

    @Test
    void ipRotationCannotExceedPerPhoneUnboundPinBudget() {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 90000) + 10000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "IP Rotation Gym " + suffix, "Owner IPR", "+91974" + String.format("%07d", Integer.parseInt(suffix)),
                "ipr" + suffix + "@test.in", "Lucknow", "Uttar Pradesh", null, false,
                "StrongPass!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        Gym gym = tenantService.getGym();
        gym.getSettings().setAttendanceEnabled(true);
        tenantService.updateSettings(gym.getSettings());
        String slug = gym.getSlug();

        Member target = memberService.create(
                "Budget Target", "+91975" + String.format("%07d", Integer.parseInt(suffix)),
                null, "M", null, null, "Uttar Pradesh", true, null);

        // Rotate across 20 distinct IPs (1 attempt per IP to bypass per-IP 5-attempt lockout)
        for (int i = 1; i <= CheckinService.MAX_UNBOUND_PHONE_FAILURES_24H; i++) {
            final int attempt = i;
            String rotatingIp = "203.0.113." + i;
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    publicAttendanceController.checkIn(slug,
                            new PublicAttendanceController.SelfCheckinRequest(target.getPhone(), "9999", null, null, null, null),
                            null, "Bot-" + attempt, requestFromIp(rotatingIp, null), new MockHttpServletResponse()));
            if (attempt < CheckinService.MAX_UNBOUND_PHONE_FAILURES_24H) {
                assertEquals("INVALID_PIN", ex.getCode());
            } else {
                assertEquals("PIN_PHONE_BUDGET_EXHAUSTED", ex.getCode());
            }
        }

        // Attempt 21 from a brand-new 21st IP is immediately blocked by the per-phone budget
        BusinessException blocked21 = assertThrows(BusinessException.class, () ->
                publicAttendanceController.checkIn(slug,
                        new PublicAttendanceController.SelfCheckinRequest(target.getPhone(), "8888", null, null, null, null),
                        null, "Bot-21", requestFromIp("192.0.2.210", null), new MockHttpServletResponse()));
        assertEquals("PIN_PHONE_BUDGET_EXHAUSTED", blocked21.getCode());

        // Verify the owner received a security alert in outbox
        List<OutboxMessage> alerts = outboxRepository.findByGymIdOrderByCreatedAtDesc(gymId);
        assertThat(alerts).anyMatch(m -> m.getVariables() != null
                && String.valueOf(m.getVariables().get("alert")).contains("unbound PIN failure budget"));

        // Verify rolling 24-hour window reset:
        // When failures age beyond 24 hours, the budget automatically resets without owner intervention
        Member targetInDb = memberRepository.findById(target.getId()).orElseThrow();
        List<java.time.Instant> agedFailures = targetInDb.getUnboundPinFailureTimestamps().stream()
                .map(ts -> ts.minus(java.time.Duration.ofHours(25)))
                .toList();
        targetInDb.setUnboundPinFailureTimestamps(agedFailures);
        memberRepository.save(targetInDb);

        // Attempt from a new IP now resets the budget and returns standard INVALID_PIN rather than budget exhaustion
        BusinessException resetEx = assertThrows(BusinessException.class, () ->
                publicAttendanceController.checkIn(slug,
                        new PublicAttendanceController.SelfCheckinRequest(target.getPhone(), "7777", null, null, null, null),
                        null, "Bot-After24h", requestFromIp("198.51.100.99", null), new MockHttpServletResponse()));
        assertEquals("INVALID_PIN", resetEx.getCode());
        Member afterRollingReset = memberRepository.findById(target.getId()).orElseThrow();
        assertFalse(afterRollingReset.isUnboundPinBudgetExhausted(), "Budget flag must auto-reset when failures are >24h old");
    }

    @Test
    void boundDeviceStillChecksInDuringIpRotationAttack() {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 90000) + 10000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Bound Safe Gym " + suffix, "Owner BS", "+91976" + String.format("%07d", Integer.parseInt(suffix)),
                "bs" + suffix + "@test.in", "Lucknow", "Uttar Pradesh", null, false,
                "StrongPass!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        Gym gym = tenantService.getGym();
        gym.getSettings().setAttendanceEnabled(true);
        tenantService.updateSettings(gym.getSettings());
        String slug = gym.getSlug();

        Plan plan = planService.create("Monthly", 30, 100_000L);
        Member member = memberService.create(
                "Bound Member", "+91977" + String.format("%07d", Integer.parseInt(suffix)),
                null, "F", null, null, "Uttar Pradesh", true, null);
        String realPin = member.getGeneratedPin();
        membershipService.enrol(member.getId(), plan.getId());

        // 1. Member binds their phone legitimately on Day 1
        Map<String, Object> firstCheckin = publicAttendanceController.checkIn(slug,
                new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), realPin, null, null, null, null),
                null, "Member-iPhone", requestFromIp("198.51.100.10", null), new MockHttpServletResponse());
        String boundDeviceToken = (String) firstCheckin.get("deviceToken");
        assertNotNull(boundDeviceToken);
        assertEquals(true, firstCheckin.get("deviceBound"));

        // Clear today's check-in so member can check in again after the attack starts
        checkinRepository.deleteAll();

        // 2. Attacker rotates across 20 IPs and exhausts the member's unbound per-phone PIN budget
        for (int i = 1; i <= CheckinService.MAX_UNBOUND_PHONE_FAILURES_24H; i++) {
            String ip = "203.0.113." + (50 + i);
            assertThrows(BusinessException.class, () ->
                    publicAttendanceController.checkIn(slug,
                            new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), "0000", null, null, null, null),
                            null, "Attacker", requestFromIp(ip, null), new MockHttpServletResponse()));
        }

        // 3. Member's already-bound device still checks in with 1 tap during the active attack!
        Map<String, Object> boundCheckin = publicAttendanceController.checkIn(slug,
                new PublicAttendanceController.SelfCheckinRequest(null, null, boundDeviceToken, null, null, null),
                boundDeviceToken, "Member-iPhone", requestFromIp("198.51.100.10", boundDeviceToken), new MockHttpServletResponse());
        assertEquals("CHECKED_IN", boundCheckin.get("status"));
        assertEquals("GREEN", boundCheckin.get("badgeColor"));
        assertEquals(member.getId(), boundCheckin.get("memberId"));
    }

    @Test
    void ownerApprovalCompletesPendingDeviceBindingAfterBudgetExhausted() {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 90000) + 10000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Owner Approval Gym " + suffix, "Owner OA", "+91978" + String.format("%07d", Integer.parseInt(suffix)),
                "oa" + suffix + "@test.in", "Lucknow", "Uttar Pradesh", null, false,
                "StrongPass!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        Gym gym = tenantService.getGym();
        gym.getSettings().setAttendanceEnabled(true);
        tenantService.updateSettings(gym.getSettings());
        String slug = gym.getSlug();

        Plan plan = planService.create("Monthly", 30, 100_000L);
        Member member = memberService.create(
                "New Phone Member", "+91979" + String.format("%07d", Integer.parseInt(suffix)),
                null, "M", null, null, "Uttar Pradesh", true, null);
        String realPin = member.getGeneratedPin();
        membershipService.enrol(member.getId(), plan.getId());

        // 1. Exhaust the per-phone unbound PIN budget across 20 IPs
        for (int i = 1; i <= CheckinService.MAX_UNBOUND_PHONE_FAILURES_24H; i++) {
            String ip = "198.51.100." + (100 + i);
            assertThrows(BusinessException.class, () ->
                    publicAttendanceController.checkIn(slug,
                            new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), "0000", null, null, null, null),
                            null, "Attacker", requestFromIp(ip, null), new MockHttpServletResponse()));
        }

        // 2. Member tries to bind a new unbound device using the VALID PIN -> creates PENDING_OWNER_APPROVAL request
        MockHttpServletResponse pendingRes = new MockHttpServletResponse();
        Map<String, Object> pendingAttempt = publicAttendanceController.checkIn(slug,
                new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), realPin, null, null, null, null),
                null, "New-Android-Phone", requestFromIp("192.0.2.55", null), pendingRes);
        assertEquals("PENDING_OWNER_APPROVAL", pendingAttempt.get("status"));
        assertEquals(false, pendingAttempt.get("deviceBound"));
        String pendingRequestId = (String) pendingAttempt.get("pendingRequestId");
        String issuedDeviceToken = (String) pendingAttempt.get("deviceToken");
        assertNotNull(pendingRequestId);
        assertNotNull(issuedDeviceToken);

        // Before owner approval, one-tap check-in with the pending device token is rejected with 403 DEVICE_PENDING_APPROVAL
        BusinessException stillPending = assertThrows(BusinessException.class, () ->
                publicAttendanceController.checkIn(slug,
                        new PublicAttendanceController.SelfCheckinRequest(null, null, issuedDeviceToken, null, null, null),
                        issuedDeviceToken, "New-Android-Phone", requestFromIp("192.0.2.55", issuedDeviceToken), new MockHttpServletResponse()));
        assertEquals("DEVICE_PENDING_APPROVAL", stillPending.getCode());

        // 3. Owner approves the pending device request from the member detail API
        Member afterApprove = memberController.approvePendingDevice(member.getId(), pendingRequestId);
        assertTrue(afterApprove.getPendingDeviceRequests().isEmpty());
        assertEquals(1, afterApprove.getBoundDevices().size());
        assertFalse(afterApprove.isUnboundPinBudgetExhausted());

        // 4. Now the newly approved device checks in with 1 tap!
        Map<String, Object> approvedCheckin = publicAttendanceController.checkIn(slug,
                new PublicAttendanceController.SelfCheckinRequest(null, null, issuedDeviceToken, null, null, null),
                issuedDeviceToken, "New-Android-Phone", requestFromIp("192.0.2.55", issuedDeviceToken), new MockHttpServletResponse());
        assertEquals("CHECKED_IN", approvedCheckin.get("status"));
        assertEquals("GREEN", approvedCheckin.get("badgeColor"));
    }

    @Test
    void configurableGstPerGymAndPerItemTypePrintsRateSacAndItcStatusOnInvoiceAndPdf() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 90000) + 10000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "GST Config Gym " + suffix, "Owner GST", "+91980" + String.format("%07d", Integer.parseInt(suffix)),
                "gst" + suffix + "@test.in", "Lucknow", "Uttar Pradesh", "09AABCU9603R1ZM", true,
                "StrongPass!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        // Configure per-gym, per-item-type GST rules:
        // - MEMBERSHIP: SAC 999723, 5%, NO_ITC
        // - PT: SAC 999723, 18%, WITH_ITC (e.g. CA-advised standard rate with ITC)
        // - ADMISSION_FEE: SAC 999723, 5%, NO_ITC
        Gym gym = tenantService.getGym();
        Gym.Settings settings = gym.getSettings();
        Map<String, Gym.GstItemRule> customRules = new LinkedHashMap<>();
        customRules.put(GstConfig.ITEM_MEMBERSHIP,
                new Gym.GstItemRule("999723", 5, LocalDate.of(2025, 9, 22), GstConfig.ITC_NO));
        customRules.put(GstConfig.ITEM_PT,
                new Gym.GstItemRule("999723", 18, LocalDate.of(2025, 9, 22), GstConfig.ITC_YES));
        customRules.put(GstConfig.ITEM_ADMISSION_FEE,
                new Gym.GstItemRule("999723", 5, LocalDate.of(2025, 9, 22), GstConfig.ITC_NO));
        settings.setGstRules(customRules);
        tenantService.updateSettings(settings);

        Member member = memberService.create(
                "GST Client", "+91989" + String.format("%07d", Integer.parseInt(suffix)),
                null, "M", null, null, "Uttar Pradesh", true, null);

        // 1. Membership + Admission Fee invoice
        Plan plan = planService.create("Gold Plan", 30, 105_000L, 21_000L);
        Membership ms = membershipService.enrol(member.getId(), plan.getId(), 21_000L, 0L, null);
        Invoice memInv = billingService.generateInvoice(ms.getId());
        assertEquals(2, memInv.getLines().size());

        Invoice.Line memLine = memInv.getLines().get(0);
        assertEquals(GstConfig.ITEM_MEMBERSHIP, memLine.getItemType());
        assertEquals("999723", memLine.getSac());
        assertEquals(5, memLine.getGstRate());
        assertEquals(GstConfig.ITC_NO, memLine.getItcStatus());

        Invoice.Line admLine = memInv.getLines().get(1);
        assertEquals(GstConfig.ITEM_ADMISSION_FEE, admLine.getItemType());
        assertEquals("999723", admLine.getSac());
        assertEquals(5, admLine.getGstRate());
        assertEquals(GstConfig.ITC_NO, admLine.getItcStatus());

        byte[] memPdf = billingService.renderPdf(memInv.getId());
        try (PdfReader reader = new PdfReader(memPdf)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("999723").contains("@5%").contains("[No ITC]");
        }

        // 2. Personal Training invoice uses the configured PT rule (SAC 999723, 18%, With ITC)
        Trainer trainer = ptController.createTrainer(new PtController.TrainerRequest(
                "Coach GST", "+91988" + String.format("%07d", Integer.parseInt(suffix)),
                true, CommissionRule.percentOfTaxable(1500)));
        PtPackage ptPkg = ptController.createPackage(new PtController.PtPackageRequest(
                "PT 10 Sessions", PtPackage.TYPE_SESSION_BASED, 60, 10, 118_000L, null, null));
        PtEnrolment ptEnrolment = ptController.sellPt(new PtController.SellPtRequest(
                member.getId(), ptPkg.getId(), trainer.getId(), 0L));

        Invoice ptInv = billingService.get(ptEnrolment.getInvoiceId());
        Invoice.Line ptLine = ptInv.getLines().get(0);
        assertEquals(GstConfig.ITEM_PT, ptLine.getItemType());
        assertEquals("999723", ptLine.getSac());
        assertEquals(18, ptLine.getGstRate());
        assertEquals(GstConfig.ITC_YES, ptLine.getItcStatus());
        assertEquals(100_000L, ptLine.getTaxablePaise());

        byte[] ptPdf = billingService.renderPdf(ptInv.getId());
        try (PdfReader reader = new PdfReader(ptPdf)) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("999723").contains("@18%").contains("[With ITC]");
        }
    }

    @Test
    void commissionSlabEdgesAndRefundReversals() {
        CommissionRule.Slab slab1 = new CommissionRule.Slab(500_000L, 1000);
        CommissionRule.Slab slab2 = new CommissionRule.Slab(null, 2000);
        CommissionRule rule = CommissionRule.slabRule(List.of(slab1, slab2));

        long c1 = CommissionCalculator.computeTotalPackageCommission(rule, 500_000L, null, 0L);
        assertEquals(50_000L, c1);

        long c2 = CommissionCalculator.computeTotalPackageCommission(rule, 800_000L, null, 0L);
        assertEquals(110_000L, c2);

        long p1 = CommissionCalculator.computeProportionalPaymentAccrual(c2, 800_000L, 0L, 480_000L);
        assertEquals(66_000L, p1);

        long p2 = CommissionCalculator.computeProportionalPaymentAccrual(c2, 800_000L, 480_000L, 320_000L);
        assertEquals(44_000L, p2);
        assertEquals(c2, p1 + p2);
    }

    @Test
    void memberSelfServicePtSessionDoneDoesNotAccrueCommissionUnilaterally() {
        String suffix = String.valueOf(System.nanoTime() % 100000);
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "PT Safe Gym " + suffix, "PT Safe Owner", "+91984" + String.format("%07d", Integer.parseInt(suffix)),
                "ptsafe" + suffix + "@test.in", "Bangalore", "Karnataka", null, false,
                "StrongPass!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        Gym gym = tenantService.getGym();
        gym.getSettings().setAttendanceEnabled(true);
        gym.getSettings().setCommissionTrigger(Gym.Settings.TRIGGER_ON_SESSION);
        tenantService.updateSettings(gym.getSettings());

        Trainer trainer = ptController.createTrainer(new PtController.TrainerRequest(
                "Trainer Safe", "+91985" + String.format("%07d", Integer.parseInt(suffix)),
                true, CommissionRule.percentOfTaxable(2000)));

        PtPackage pkg = ptController.createPackage(new PtController.PtPackageRequest(
                "5-Session PT", PtPackage.TYPE_SESSION_BASED, 30, 5, 500_000L, "999723", null));

        Member member = memberService.create(
                "Client Safe", "+91986" + String.format("%07d", Integer.parseInt(suffix)),
                null, "F", null, null, "Karnataka", true, null);

        PtEnrolment enrolment = ptController.sellPt(new PtController.SellPtRequest(
                member.getId(), pkg.getId(), trainer.getId(), 0L));

        ptService.markSessionDoneForMemberSelfService(gymId, member.getId());
        PtEnrolment afterSelf = ptService.listEnrolmentsByMember(member.getId()).getFirst();
        assertEquals(1, afterSelf.getDeliveredSessions());

        String month = YearMonth.now().toString();
        Map<String, Object> stmt = ptController.statement(trainer.getId(), month);
        assertEquals(0L, ((Number) stmt.get("accruedPaise")).longValue());

        ptController.markSessionDone(enrolment.getId());
        Map<String, Object> stmtAfterOwner = ptController.statement(trainer.getId(), month);
        assertTrue(((Number) stmtAfterOwner.get("accruedPaise")).longValue() > 0L);
    }
}
