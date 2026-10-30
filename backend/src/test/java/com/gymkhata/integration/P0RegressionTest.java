package com.gymkhata.integration;

import com.gymkhata.AbstractIntegrationTest;
import com.gymkhata.admin.AdminService;
import com.gymkhata.auth.AuthController;
import com.gymkhata.auth.AuthService;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.repo.PaymentRepository;
import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.checkin.repo.CheckinRepository;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.importer.ImporterService;
import com.gymkhata.member.MemberService;
import com.gymkhata.member.model.Member;
import com.gymkhata.report.ReportService;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Self-contained regression suite for the 5 original P0 audit findings:
 * - P0-SEC-1: Unauthenticated password-reset token leakage
 * - P0-SEC-2: Refresh-token rotation overwriting familyId (failing to revoke active child tokens)
 * - P0-SEC-3: Missing fail-fast production configuration guards (JWT_SECRET, SUPER_ADMIN_PASSWORD, CORS)
 * - P0-BUG-1: CSV payment import discarding all rows instead of persisting Payment records
 * - P0-BUG-2: ReportService.checkinsPerDay BSON Date vs String mismatch / ClassCastException
 */
class P0RegressionTest extends AbstractIntegrationTest {

    @Autowired private AdminService adminService;
    @Autowired private AuthController authController;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private MemberService memberService;
    @Autowired private ImporterService importerService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private CheckinRepository checkinRepository;
    @Autowired private ReportService reportService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void p0Sec1_passwordResetRequestMustNotLeakPlaintextResetToken() {
        String phone = "+919500000101";
        adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P0 SEC 1 Gym", "Owner 1", phone, null,
                "Lucknow", "Uttar Pradesh", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        Map<String, String> res = authController.requestReset(new AuthController.ResetRequest(phone));
        assertFalse(res.containsKey("resetToken"),
                "P0-SEC-1: /api/v1/auth/password-reset/request leaked plaintext resetToken in public response: " + res);
    }

    @Test
    void p0Sec2_refreshTokenReuseMustRevokeActiveChildInSameFamily() {
        String phone = "+919500000102";
        adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P0 SEC 2 Gym", "Owner 2", phone, null,
                "Indore", "Madhya Pradesh", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        AuthService.TokenPair first = authService.login(phone, "Passw0rd!23");
        AuthService.TokenPair second = authService.refresh(first.refreshToken());

        // Replaying the rotated parent token triggers reuse detection
        assertThrows(BusinessException.class, () -> authService.refresh(first.refreshToken()));

        // The active child token in the same family MUST now be revoked as well
        assertThrows(BusinessException.class, () -> authService.refresh(second.refreshToken()),
                "P0-SEC-2: Active child refresh token remained valid after parent token reuse because familyId was overwritten!");
    }

    @Test
    void p0Sec3_prodProfileMustFailFastOnDefaultJwtSecretAndWeakSuperAdminPassword() throws Exception {
        assertNotNull(getClass().getResource("/application-prod.yml"),
                "P0-SEC-3: Missing /application-prod.yml production profile configuration");

        Class<?> validatorClass;
        try {
            validatorClass = Class.forName("com.gymkhata.common.config.ProdConfigValidator");
        } catch (ClassNotFoundException e) {
            fail("P0-SEC-3: Missing ProdConfigValidator fail-fast startup guard");
            return;
        }

        Method validate = validatorClass.getMethod("validateProdSettings", String.class, String.class, String.class);

        // Must reject default dev JWT secret
        InvocationTargetException ex1 = assertThrows(InvocationTargetException.class, () ->
                validate.invoke(null, "gymkhata-dev-secret-change-me-0123456789abcdef0123456789abcdef",
                        "StrongProdAdmin#2026", "https://app.gymkhata.in"));
        assertInstanceOf(IllegalStateException.class, ex1.getCause());

        // Must reject default ChangeMe@123 super-admin password
        InvocationTargetException ex2 = assertThrows(InvocationTargetException.class, () ->
                validate.invoke(null, "prod-random-secret-key-with-more-than-32-bytes-entropy-2026",
                        "ChangeMe@123", "https://app.gymkhata.in"));
        assertInstanceOf(IllegalStateException.class, ex2.getCause());

        // Must reject wildcard CORS origin in prod
        InvocationTargetException ex3 = assertThrows(InvocationTargetException.class, () ->
                validate.invoke(null, "prod-random-secret-key-with-more-than-32-bytes-entropy-2026",
                        "StrongProdAdmin#2026", "*"));
        assertInstanceOf(IllegalStateException.class, ex3.getCause());
    }

    @Test
    void p0Bug1_csvPaymentImportMustPersistPaymentsToDatabaseWithExactPaise() {
        String ownerPhone = "+919500000104";
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P0 BUG 1 Gym", "Owner 4", ownerPhone, null,
                "Jaipur", "Rajasthan", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        memberService.create("CSV Pay Member", "+919510000104", null, "M", null, null, "Rajasthan", true, null);

        String csv = "phone,amount,method,utr\n+919510000104,19.99,CASH,\n";
        ImporterService.ImportResult res = importerService.importPayments(csv);
        assertEquals(1, res.success());

        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> gymId.equals(p.getGymId()))
                .toList();
        assertFalse(payments.isEmpty(),
                "P0-BUG-1: ImporterService.importPayments discarded all rows and persisted 0 Payment documents!");
        assertEquals(1999L, payments.get(0).getAmountPaise());
    }

    @Test
    void p0Bug2_checkinsPerDayReportMustReturnTodayCheckinsWithoutDateTypeMismatch() {
        String ownerPhone = "+919500000105";
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P0 BUG 2 Gym", "Owner 5", ownerPhone, null,
                "Pune", "Maharashtra", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        Member m = memberService.create("Report Member", "+919510000105", null, "F", null, null, "Maharashtra", true, null);
        Checkin c = new Checkin();
        c.setGymId(gymId);
        c.setMemberId(m.getId());
        c.setMemberName(m.getName());
        c.setDate(LocalDate.now());
        c.setCheckInAt(Instant.now());
        checkinRepository.save(c);

        List<Document> rows = reportService.checkinsPerDay(30);
        assertFalse(rows.isEmpty(),
                "P0-BUG-2: ReportService.checkinsPerDay returned 0 rows due to BSON Date vs String comparison!");
        assertEquals(LocalDate.now().toString(), rows.get(0).getString("date"));
        assertTrue(rows.get(0).getInteger("count") >= 1);
    }
}
