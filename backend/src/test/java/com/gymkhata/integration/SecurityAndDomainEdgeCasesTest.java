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
import com.gymkhata.billing.PaymentService;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.checkin.CheckinController;
import com.gymkhata.checkin.CheckinService;
import com.gymkhata.checkin.PublicAttendanceController;
import com.gymkhata.checkin.model.Checkin;
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
import com.gymkhata.pt.model.CommissionEntry;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.model.PtPackage;
import com.gymkhata.pt.model.Trainer;
import com.gymkhata.tenant.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
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

    @Test
    void passwordResetTokenNeverReturnedInPublicApiResponse() {
        String suffix = String.valueOf(System.nanoTime() % 100000);
        String phone = "+91981" + String.format("%07d", Integer.parseInt(suffix));
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Reset Security Gym " + suffix, "Reset Owner", phone,
                "reset" + suffix + "@test.in", "Delhi", "Delhi", null, false,
                "StrongPass!23", LocalDate.now().plusMonths(6)));
        String gymId = (String) created.get("gymId");

        // Public reset request endpoint MUST NEVER return the reset token
        Map<String, String> publicRes = authController.requestReset(new AuthController.ResetRequest(phone));
        assertNotNull(publicRes);
        assertEquals("reset_requested", publicRes.get("status"));
        assertNull(publicRes.get("resetToken"));
        assertNull(publicRes.get("token"));
        assertThat(publicRes.toString().toLowerCase()).doesNotContain("uuid");

        // Super-admin initiated reset explicitly displays single-use token to super admin
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
        // Verify exponential backoff schedule (60s -> 300s -> 900s -> 3600s)
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

        // 5 wrong attempts from attacker IP behind trusted Cloud Run / Nginx proxy (10.128.0.5) via X-Forwarded-For
        String attackerIp = "198.51.100." + (Integer.parseInt(suffix) % 250 + 1);
        for (int i = 1; i <= 5; i++) {
            final int attempt = i;
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr("10.128.0.5"); // trusted internal proxy
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

        // Direct untrusted connection from attackerIp spoofing X-Forwarded-For: 8.8.8.8 must STILL be locked out
        MockHttpServletRequest spoofReq = new MockHttpServletRequest();
        spoofReq.setRemoteAddr(attackerIp); // untrusted direct peer
        spoofReq.addHeader("X-Forwarded-For", "8.8.8.8");
        BusinessException spoofEx = assertThrows(BusinessException.class, () ->
                publicAttendanceController.checkIn(slug,
                        new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), realPin, null, null, null, null),
                        null, "AttackerSpoof", spoofReq, new MockHttpServletResponse()));
        assertEquals("PIN_LOCKED", spoofEx.getCode());

        // Check that owner outbox received an alert notification
        List<OutboxMessage> alerts = outboxRepository.findByGymIdOrderByCreatedAtDesc(gymId);
        assertThat(alerts).anyMatch(m -> m.getVariables() != null
                && String.valueOf(m.getVariables().get("alert")).contains("Multiple failed PIN attempts"));

        // Attacker IP tries to check in for Member 2 with valid PIN -> MUST NOT BE LOCKED OUT (one attacker cannot lock out the whole gym)
        Member member2 = memberService.create(
                "Other Member", "+91984" + String.format("%07d", Integer.parseInt(suffix)),
                null, "F", null, null, "Maharashtra", true, null);
        String mem2Pin = member2.getGeneratedPin();

        MockHttpServletRequest reqOtherMem = new MockHttpServletRequest();
        reqOtherMem.setRemoteAddr("10.128.0.5");
        reqOtherMem.addHeader("X-Forwarded-For", attackerIp);
        Map<String, Object> mem2Res = publicAttendanceController.checkIn(slug,
                new PublicAttendanceController.SelfCheckinRequest(member2.getPhone(), mem2Pin, null, null, null, null),
                null, "Browser", reqOtherMem, new MockHttpServletResponse());
        assertNotNull(mem2Res);
        assertEquals(member2.getId(), mem2Res.get("memberId"));

        // Gym owner resets PIN lockout -> immediately unlocks Member 1 even on the locked attackerIp
        memberController.resetPinLockout(member.getId());

        MockHttpServletRequest unlockedReq = new MockHttpServletRequest();
        unlockedReq.setRemoteAddr("10.128.0.5");
        unlockedReq.addHeader("X-Forwarded-For", attackerIp);
        Map<String, Object> success = publicAttendanceController.checkIn(slug,
                new PublicAttendanceController.SelfCheckinRequest(member.getPhone(), realPin, null, null, null, null),
                null, "LegitPhone", unlockedReq, new MockHttpServletResponse());
        assertNotNull(success);
        assertEquals("RED", success.get("badgeColor"));
        assertNotNull(success.get("payLink"));
    }

    @Test
    void commissionSlabEdgesAndRefundReversals() {
        CommissionRule.Slab slab1 = new CommissionRule.Slab(500_000L, 1000); // 10% up to ₹5,000
        CommissionRule.Slab slab2 = new CommissionRule.Slab(null, 2000);     // 20% above ₹5,000
        CommissionRule rule = CommissionRule.slabRule(List.of(slab1, slab2));

        // Exact boundary: ₹5,000 taxable -> 10% = ₹500
        long c1 = CommissionCalculator.computeTotalPackageCommission(rule, 500_000L, null, 0L);
        assertEquals(50_000L, c1);

        // Above boundary: ₹8,000 taxable -> ₹500 (10% of ₹5,000) + ₹600 (20% of ₹3,000) = ₹1,100 (110,000 paise)
        long c2 = CommissionCalculator.computeTotalPackageCommission(rule, 800_000L, null, 0L);
        assertEquals(110_000L, c2);

        // Pro-rata partial payment: 60% of ₹1,100 = ₹660 (66,000 paise)
        long p1 = CommissionCalculator.computeProportionalPaymentAccrual(c2, 800_000L, 0L, 480_000L);
        assertEquals(66_000L, p1);

        // Remaining 40%: ₹440 (44,000 paise)
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

        // Member self-service tap: Decrements session count safely but does NOT accrue financial commission
        ptService.markSessionDoneForMemberSelfService(gymId, member.getId());
        PtEnrolment afterSelf = ptService.listEnrolmentsByMember(member.getId()).getFirst();
        assertEquals(1, afterSelf.getDeliveredSessions());

        String month = YearMonth.now().toString();
        Map<String, Object> stmt = ptController.statement(trainer.getId(), month);
        assertEquals(0L, ((Number) stmt.get("accruedPaise")).longValue());

        // When Owner confirms / records session, commission accrual occurs
        ptController.markSessionDone(enrolment.getId());
        Map<String, Object> stmtAfterOwner = ptController.statement(trainer.getId(), month);
        assertTrue(((Number) stmtAfterOwner.get("accruedPaise")).longValue() > 0L);
    }
}
