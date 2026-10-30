package com.gymkhata.integration;

import com.gymkhata.AbstractIntegrationTest;
import com.gymkhata.admin.AdminService;
import com.gymkhata.admin.model.Gym;
import com.gymkhata.auth.AuthController;
import com.gymkhata.auth.AuthService;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.billing.BillingService;
import com.gymkhata.billing.PaymentService;
import com.gymkhata.billing.PublicPayController;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.checkin.CheckinService;
import com.gymkhata.common.config.ProdConfigValidator;
import com.gymkhata.common.config.RateLimitFilter;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.importer.ImporterService;
import com.gymkhata.lead.LeadService;
import com.gymkhata.lead.model.Lead;
import com.gymkhata.member.MemberService;
import com.gymkhata.member.model.Member;
import com.gymkhata.membership.MembershipService;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.plan.PlanService;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.report.ReportService;
import com.gymkhata.tenant.TenantService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ComprehensiveFlowAndSecurityTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminService adminService;
    @Autowired private AuthService authService;
    @Autowired private AuthController authController;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantService tenantService;
    @Autowired private PlanService planService;
    @Autowired private MemberService memberService;
    @Autowired private MembershipService membershipService;
    @Autowired private BillingService billingService;
    @Autowired private PaymentService paymentService;
    @Autowired private PublicPayController publicPayController;
    @Autowired private CheckinService checkinService;
    @Autowired private LeadService leadService;
    @Autowired private ReportService reportService;
    @Autowired private ImporterService importerService;
    @Autowired private RateLimitFilter rateLimitFilter;

    private void loginAs(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void refreshTokenReuseRevokesEntireTokenFamilyIncludingActiveChild() {
        adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Family Token Gym", "Owner FT", "+919400000001", null,
                "Lucknow", "Uttar Pradesh", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        AuthService.TokenPair first = authService.login("+919400000001", "Passw0rd!23");
        AuthService.TokenPair second = authService.refresh(first.refreshToken());

        // Replaying the old (first) refresh token triggers family revocation
        BusinessException reuseEx = assertThrows(BusinessException.class,
                () -> authService.refresh(first.refreshToken()));
        assertEquals("REFRESH_REUSE_DETECTED", reuseEx.getCode());

        // Now the active child token (second) MUST ALSO be revoked!
        BusinessException childEx = assertThrows(BusinessException.class,
                () -> authService.refresh(second.refreshToken()));
        assertEquals("REFRESH_REUSE_DETECTED", childEx.getCode());
    }

    @Test
    void passwordResetRequestNeverLeaksPlaintextTokenInApiResponseAndChangePasswordWorks() {
        adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Reset Safe Gym", "Owner RS", "+919400000002", null,
                "Indore", "Madhya Pradesh", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        Map<String, String> response = authController.requestReset(new AuthController.ResetRequest("+919400000002"));
        assertThat(response).doesNotContainKey("resetToken");
        assertEquals("reset_requested", response.get("status"));

        // Authenticated owner can change their own password
        AuthService.TokenPair pair = authService.login("+919400000002", "Passw0rd!23");
        loginAs(pair.user());
        authController.changePassword(new AuthController.ChangePasswordRequest("Passw0rd!23", "NewStrongPass!99"));

        // Old refresh token is revoked after password change
        assertThrows(BusinessException.class, () -> authService.refresh(pair.refreshToken()));

        // Login with new password succeeds
        assertDoesNotThrow(() -> authService.login("+919400000002", "NewStrongPass!99"));
    }

    @Test
    void prodConfigValidatorFailsFastOnInsecureOrDefaultSettings() {
        String validSecret = "prod-random-secret-key-with-more-than-32-bytes-entropy-2026";
        String validPass = "StrongProdAdmin#2026";
        String validCors = "https://app.gymkhata.in,https://gymkhata.in";

        assertDoesNotThrow(() -> ProdConfigValidator.validateProdSettings(validSecret, validPass, validCors));

        // 1. Dev default JWT secret rejected
        assertThatThrownBy(() -> ProdConfigValidator.validateProdSettings(
                ProdConfigValidator.DEV_JWT_SECRET, validPass, validCors))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");

        // 2. Short JWT secret rejected
        assertThatThrownBy(() -> ProdConfigValidator.validateProdSettings(
                "short-secret", validPass, validCors))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");

        // 3. Default super-admin password ChangeMe@123 rejected
        assertThatThrownBy(() -> ProdConfigValidator.validateProdSettings(
                validSecret, "ChangeMe@123", validCors))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPER_ADMIN_PASSWORD");

        // 4. Demo super-admin password rejected in prod
        assertThatThrownBy(() -> ProdConfigValidator.validateProdSettings(
                validSecret, "DemoAdmin@2026", validCors))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPER_ADMIN_PASSWORD");

        // 5. Missing, wildcard, or localhost CORS origin rejected in prod
        assertThatThrownBy(() -> ProdConfigValidator.validateProdSettings(validSecret, validPass, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
        assertThatThrownBy(() -> ProdConfigValidator.validateProdSettings(validSecret, validPass, "*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
        assertThatThrownBy(() -> ProdConfigValidator.validateProdSettings(validSecret, validPass, "http://localhost:5173"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void securityHeadersActuatorLockdownAndRateLimitingWork() throws Exception {
        // 1. /actuator/health is public and includes security headers
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Referrer-Policy"));

        // 2. /actuator/info is blocked for unauthenticated callers
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());

        // 3. Rate limiting respects X-Forwarded-For and returns 429 after threshold
        rateLimitFilter.reset();
        rateLimitFilter.setMaxRequestsPerMinute(3);
        try {
            String body = "{\"phone\":\"+919999999999\",\"password\":\"wrongpass\"}";
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-Forwarded-For", "203.0.113.55")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isForbidden());
            }
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "203.0.113.55")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "60"));
            // Another client IP behind the same proxy is NOT rate-limited
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "203.0.113.99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        } finally {
            rateLimitFilter.setMaxRequestsPerMinute(60);
            rateLimitFilter.reset();
        }
    }

    @Test
    void disabledUserAccessTokenIsImmediatelyRejectedByJwtAuthenticationFilter() throws Exception {
        String phone = "+919400000088";
        adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "JWT Status Gym", "Owner JWT", phone, null,
                "Pune", "Maharashtra", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        AuthService.TokenPair pair = authService.login(phone, "Passw0rd!23");

        // Active user token succeeds
        mockMvc.perform(get("/api/v1/tenant/gym")
                        .header("Authorization", "Bearer " + pair.accessToken()))
                .andExpect(status().isOk());

        // Disable user in DB while access token is still unexpired
        User u = userRepository.findById(pair.user().getId()).orElseThrow();
        u.setStatus("DISABLED");
        userRepository.save(u);

        // Unexpired access token is immediately rejected by JwtAuthenticationFilter
        mockMvc.perform(get("/api/v1/tenant/gym")
                        .header("Authorization", "Bearer " + pair.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void multipleDpdpErasedMembersProfileUpdateRejectUtrPublicPayBalanceAndHindiPdfWork() {
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "बजरंग पावरहाउस (Bajrang Gym)", "Rajesh Singh", "+919400000003", "rajesh@bajrang.in",
                "Lucknow", "Uttar Pradesh", "09AABCB1234F1Z5", true, "Passw0rd!23", LocalDate.now().plusMonths(3)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        // 1. TenantService.updateProfile persists phone, email, address, city, state
        Gym updatedGym = tenantService.updateProfile(
                "बजरंग पावरहाउस लखनऊ",
                "09400000099",
                "contact@bajrang.in",
                "42 Hazratganj Main Road",
                "Lucknow",
                "Uttar Pradesh",
                "09AABCB1234F1Z5",
                true,
                "BPF"
        );
        assertEquals("+919400000099", updatedGym.getPhone());
        assertEquals("contact@bajrang.in", updatedGym.getEmail());
        assertEquals("42 Hazratganj Main Road", updatedGym.getAddress());
        assertEquals("Lucknow", updatedGym.getCity());
        assertEquals("Uttar Pradesh", updatedGym.getState());

        // 2. DPDP Erase works for multiple members in the same gym without DuplicateKeyException
        Member m1 = memberService.create("Erase One", "+919410000001", null, "M", null, null, "Uttar Pradesh", true, null);
        Member m2 = memberService.create("Erase Two", "+919410000002", null, "F", null, null, "Uttar Pradesh", true, null);
        memberService.erase(m1.getId());
        assertDoesNotThrow(() -> memberService.erase(m2.getId()));

        // 3. Create Hindi member, enrol, generate invoice, render PDF with Unicode Devanagari font
        Member hindiMember = memberService.create("रोहित शर्मा", "+919410000003", "rohit@demo.in", "M",
                null, "Gomti Nagar", "Uttar Pradesh", true, "सुबह का बैच");
        Plan plan = planService.create("Silver Monthly ₹1,180 (मासिक)", 30, 118000L);
        Membership ms = membershipService.enrol(hindiMember.getId(), plan.getId());
        Invoice inv = billingService.generateInvoice(ms.getId());

        byte[] pdfBytes = billingService.renderPdf(inv.getId());
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // 4. Partial cash payment of ₹500 -> PublicPayController reports ₹680 balance due and status PARTIAL
        paymentService.recordCounterPayment(inv.getId(), 50000L, Payment.METHOD_CASH);
        Map<String, Object> payPage = publicPayController.payPage(inv.getPayToken());
        assertEquals(68000L, payPage.get("amountPaise"));
        assertEquals(118000L, payPage.get("totalPaise"));
        assertEquals(50000L, payPage.get("paidPaise"));
        assertEquals("PARTIAL", payPage.get("status"));

        // 5. Member submits UTR for remaining ₹680 -> Owner REJECTS it -> balance stays ₹680
        Payment badUpi = publicPayController.submitUtr(inv.getPayToken(),
                new PublicPayController.UtrSubmitRequest(68000L, "711122223333"));
        assertEquals(Payment.STATUS_PENDING, badUpi.getStatus());

        Payment rejected = paymentService.rejectUpi(badUpi.getId(), "UTR not in statement");
        assertEquals(Payment.STATUS_FAILED, rejected.getStatus());
        assertEquals(68000L, paymentService.balanceDue(gymId, inv.getId()));

        // 6. Member submits valid UTR -> Owner CONFIRMS it -> PublicPayController reports status PAID and 0 balance
        Payment goodUpi = publicPayController.submitUtr(inv.getPayToken(),
                new PublicPayController.UtrSubmitRequest(68000L, "711122224444"));
        paymentService.confirmUpi(goodUpi.getId());
        assertEquals(0L, paymentService.balanceDue(gymId, inv.getId()));
        Map<String, Object> settledPayPage = publicPayController.payPage(inv.getPayToken());
        assertEquals("PAID", settledPayPage.get("status"));
        assertEquals(0L, settledPayPage.get("amountPaise"));

        // 7. Check-in + ReportService.checkinsPerDay works without ClassCastException (after enabling attendance module)
        Gym.Settings attSettings = updatedGym.getSettings() == null ? new Gym.Settings() : updatedGym.getSettings();
        attSettings.setAttendanceEnabled(true);
        tenantService.updateSettings(attSettings);
        checkinService.checkIn(hindiMember.getId());
        List<Document> dailyCheckins = reportService.checkinsPerDay(30);
        assertThat(dailyCheckins).isNotEmpty();
        assertThat(dailyCheckins.get(0).getString("date")).isEqualTo(LocalDate.now().toString());
        assertThat(dailyCheckins.get(0).getInteger("count")).isGreaterThanOrEqualTo(1);

        // 8. Lead create -> convert to member
        Lead lead = leadService.create("Priya Walkin", "+919410000009", "WALK_IN", "Interested in monthly");
        Member converted = leadService.convert(lead.getId());
        assertEquals("+919410000009", converted.getPhone());
    }

    @Test
    void csvImporterSavesPaymentsAndEnforcesRowLimitHeaderEncodingAndFormulaInjectionGuards() {
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "CSV Test Gym", "Owner CSV", "+919400000004", null,
                "Jaipur", "Rajasthan", null, false, "Passw0rd!23", LocalDate.now().plusMonths(3)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        // 1. Valid members CSV import
        String validMembersCsv = """
                name,phone,email,gender,notes
                "Kiran Rao",9876540001,kiran@demo.in,F,"Morning batch"
                "अमित कुमार",+91-98765-40002,amit@demo.in,M,"Evening batch"
                """;
        ImporterService.ImportResult memRes = importerService.importMembers(validMembersCsv);
        assertEquals(2, memRes.totalRows());
        assertEquals(2, memRes.success());
        assertTrue(memRes.errors().isEmpty());

        // 2. Valid payments CSV import actually saves payments to MongoDB with exact BigDecimal paise
        String validPaymentsCsv = """
                phone,amount,method,utr
                +919876540001,999.50,CASH,
                9876540002,1180.00,UPI,812345678901
                """;
        ImporterService.ImportResult payRes = importerService.importPayments(validPaymentsCsv);
        assertEquals(2, payRes.totalRows());
        assertEquals(2, payRes.success());
        assertTrue(payRes.errors().isEmpty());

        // 3. Missing required header columns rejected
        String missingColsCsv = "email,gender\nfoo@bar.com,M\n";
        BusinessException missingEx = assertThrows(BusinessException.class,
                () -> importerService.importMembers(missingColsCsv));
        assertEquals("CSV_MISSING_COLUMNS", missingEx.getCode());

        // 4. Invalid UTF-8 replacement char rejected
        String badEncodingCsv = "name,phone\nBad\uFFFDName,9876540099\n";
        BusinessException encEx = assertThrows(BusinessException.class,
                () -> importerService.importMembers(badEncodingCsv));
        assertEquals("CSV_INVALID_ENCODING", encEx.getCode());

        // 5. > 1,000 rows rejected
        StringBuilder huge = new StringBuilder("name,phone\n");
        for (int i = 0; i < 1005; i++) {
            huge.append("Member ").append(i).append(",98").append(String.format("%08d", i)).append("\n");
        }
        BusinessException rowsEx = assertThrows(BusinessException.class,
                () -> importerService.importMembers(huge.toString()));
        assertEquals("CSV_TOO_MANY_ROWS", rowsEx.getCode());

        // 6. Formula injection cells (=, +, -, @) rejected per row
        String formulaCsv = """
                name,phone,email
                "=CMD|' /C calc'!A0",9876540101,a@b.com
                "+SUM(1,1)",9876540102,b@b.com
                "-2+3",9876540103,c@b.com
                "@IMPORTXML()",9876540104,d@b.com
                """;
        ImporterService.ImportResult formulaRes = importerService.importMembers(formulaCsv);
        assertEquals(4, formulaRes.totalRows());
        assertEquals(0, formulaRes.success());
        assertEquals(4, formulaRes.errors().size());
    }
}
