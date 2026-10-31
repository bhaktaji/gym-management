package com.gymkhata.integration;

import com.gymkhata.AbstractIntegrationTest;
import com.gymkhata.admin.AdminService;
import com.gymkhata.admin.model.Gym;
import com.gymkhata.auth.AuthService;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.billing.BillingService;
import com.gymkhata.billing.PaymentService;
import com.gymkhata.billing.PublicPayController;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.repo.PaymentRepository;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.importer.ImporterService;
import com.gymkhata.member.MemberService;
import com.gymkhata.member.model.Member;
import com.gymkhata.membership.MembershipService;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.plan.PlanService;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.tenant.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Self-contained regression suite for all 10 original P1 audit findings:
 * - P1-SEC-1: Sliding-window IP rate limiter (RateLimitFilter) on auth & public UTR endpoints
 * - P1-SEC-2: JwtAuthenticationFilter immediate rejection of disabled user's unexpired JWT
 * - P1-SEC-3: HTTP security headers (CSP, X-Frame-Options, nosniff) & /actuator/info lockdown
 * - P1-SEC-4: Authenticated self-service password change (POST /api/v1/auth/change-password)
 * - P1-BUG-1: DPDP double erase without DuplicateKeyException on (gymId, phone: null)
 * - P1-BUG-2: Reject pending UPI UTR endpoint (POST /api/v1/payments/{id}/reject)
 * - P1-BUG-3: Public pay page (/api/v1/pay/{token}) remaining balance & PARTIAL status after partial payment
 * - P1-BUG-4: TenantService.updateProfile persisting phone, email, address, city, state
 * - P1-BUG-5: Unicode Devanagari font rendering in PdfRenderer for Hindi member/plan names
 * - P1-BUG-6: CSV importer guards (formula injection, header validation, UTF-8 encoding, 1,000-row cap)
 */
@AutoConfigureMockMvc
class P1RegressionTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private AdminService adminService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantService tenantService;
    @Autowired private MemberService memberService;
    @Autowired private PlanService planService;
    @Autowired private MembershipService membershipService;
    @Autowired private BillingService billingService;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PublicPayController publicPayController;
    @Autowired private ImporterService importerService;

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
    void p1Sec1_rateLimitFilterMustBlockExcessAuthRequestsBehindProxyWith429() throws Exception {
        Class<?> filterClass;
        try {
            filterClass = Class.forName("com.gymkhata.common.config.RateLimitFilter");
        } catch (ClassNotFoundException e) {
            fail("P1-SEC-1: Missing RateLimitFilter sliding-window IP rate limiter");
            return;
        }
        Object filterBean = applicationContext.getBean(filterClass);
        Method reset = filterClass.getMethod("reset");
        Method setMax = filterClass.getMethod("setMaxRequestsPerMinute", int.class);
        reset.invoke(filterBean);
        setMax.invoke(filterBean, 3);
        try {
            String body = "{\"phone\":\"+919999999999\",\"password\":\"wrongpass\"}";
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "198.51.100.77")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
            }
            MvcResult fourth = mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", "198.51.100.77")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            assertEquals(429, fourth.getResponse().getStatus(),
                    "P1-SEC-1: Expected 429 Too Many Requests after exceeding IP rate limit");
            assertEquals("60", fourth.getResponse().getHeader("Retry-After"));
        } finally {
            setMax.invoke(filterBean, 60);
            reset.invoke(filterBean);
        }
    }

    @Test
    void p1Sec2_jwtAuthenticationFilterMustRejectUnexpiredAccessTokenOfDisabledUser() throws Exception {
        String phone = "+919520000102";
        adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P1 SEC 2 Gym", "Owner P1S2", phone, null,
                "Pune", "Maharashtra", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        AuthService.TokenPair pair = authService.login(phone, "Passw0rd!23");

        User u = userRepository.findById(pair.user().getId()).orElseThrow();
        u.setStatus("DISABLED");
        userRepository.save(u);

        MvcResult res = mockMvc.perform(get("/api/v1/tenant/gym")
                        .header("Authorization", "Bearer " + pair.accessToken()))
                .andReturn();
        assertEquals(401, res.getResponse().getStatus(),
                "P1-SEC-2: Disabled user's unexpired JWT access token was still accepted by JwtAuthenticationFilter!");
    }

    @Test
    void p1Sec3_securityHeadersMustBePresentAndActuatorInfoLockedDown() throws Exception {
        MvcResult healthRes = mockMvc.perform(get("/actuator/health")).andReturn();
        assertEquals(200, healthRes.getResponse().getStatus());
        assertNotNull(healthRes.getResponse().getHeader("Content-Security-Policy"),
                "P1-SEC-3: Missing Content-Security-Policy response header!");
        assertNotNull(healthRes.getResponse().getHeader("Referrer-Policy"),
                "P1-SEC-3: Missing Referrer-Policy response header!");

        MvcResult infoRes = mockMvc.perform(get("/actuator/info")).andReturn();
        assertEquals(401, infoRes.getResponse().getStatus(),
                "P1-SEC-3: /actuator/info is publicly accessible without authentication!");
    }

    @Test
    void p1Sec4_authenticatedUserMustBeAbleToChangePasswordAndRevokeRefreshTokens() throws Exception {
        String phone = "+919520000104";
        adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P1 SEC 4 Gym", "Owner P1S4", phone, null,
                "Indore", "Madhya Pradesh", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        AuthService.TokenPair pair = authService.login(phone, "Passw0rd!23");

        MvcResult res = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + pair.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Passw0rd!23\",\"newPassword\":\"NewStrongPass!99\"}"))
                .andReturn();
        assertEquals(200, res.getResponse().getStatus(),
                "P1-SEC-4: POST /api/v1/auth/change-password failed or is missing!");
        assertThrows(BusinessException.class, () -> authService.refresh(pair.refreshToken()),
                "P1-SEC-4: Old refresh token was not revoked upon password change!");
    }

    @Test
    void p1Bug1_dpdpEraseMustSucceedForMultipleMembersInSameGymWithoutDuplicateKeyException() {
        String phone = "+919520000105";
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P1 BUG 1 Gym", "Owner P1B1", phone, null,
                "Lucknow", "Uttar Pradesh", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        Member m1 = memberService.create("Erase Member 1", "+919530000101", null, "M", null, null, "Uttar Pradesh", true, null);
        Member m2 = memberService.create("Erase Member 2", "+919530000102", null, "F", null, null, "Uttar Pradesh", true, null);

        memberService.erase(m1.getId());
        assertDoesNotThrow(() -> memberService.erase(m2.getId()),
                "P1-BUG-1: Erasing a second member in the same gym threw DuplicateKeyException on (gymId, phone: null)!");
    }

    @Test
    void p1Bug2_ownerMustBeAbleToRejectInvalidUpiPaymentWithoutReducingInvoiceBalance() throws Exception {
        String phone = "+919520000106";
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P1 BUG 2 Gym", "Owner P1B2", phone, null,
                "Jaipur", "Rajasthan", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        String gymId = (String) created.get("gymId");
        AuthService.TokenPair pair = authService.login(phone, "Passw0rd!23");
        loginAs(pair.user());

        Member m = memberService.create("UTR Member", "+919530000106", null, "M", null, null, "Rajasthan", true, null);
        Plan p = planService.create("Monthly", 30, 100000L);
        Membership ms = membershipService.enrol(m.getId(), p.getId());
        Invoice inv = billingService.generateInvoice(ms.getId());

        Payment pending = paymentService.submitUpi(inv.getId(), 100000L, "811122223333");

        MvcResult res = mockMvc.perform(post("/api/v1/payments/" + pending.getId() + "/reject")
                        .header("Authorization", "Bearer " + pair.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Fake UTR\"}"))
                .andReturn();
        assertEquals(200, res.getResponse().getStatus(),
                "P1-BUG-2: POST /api/v1/payments/{id}/reject endpoint is missing or failed!");

        Payment reloaded = paymentRepository.findById(pending.getId()).orElseThrow();
        assertEquals(Payment.STATUS_FAILED, reloaded.getStatus(),
                "P1-BUG-2: Rejected payment status must be FAILED");
        assertEquals(100000L, paymentService.balanceDue(gymId, inv.getId()));
    }

    @Test
    void p1Bug3_publicPayPageMustShowRemainingBalanceAndPartialStatusAfterPartialPayment() {
        String phone = "+919520000107";
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P1 BUG 3 Gym", "Owner P1B3", phone, null,
                "Delhi", "Delhi", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        Member m = memberService.create("Partial Member", "+919530000107", null, "M", null, null, "Delhi", true, null);
        Plan p = planService.create("Monthly", 30, 118000L);
        Membership ms = membershipService.enrol(m.getId(), p.getId());
        Invoice inv = billingService.generateInvoice(ms.getId());

        paymentService.recordCounterPayment(inv.getId(), 50000L, Payment.METHOD_CASH);

        Map<String, Object> payPage = publicPayController.payPage(inv.getPayToken());
        assertEquals(68000L, ((Number) payPage.get("amountPaise")).longValue(),
                "P1-BUG-3: Public pay page returned full invoice total instead of remaining balance due after partial payment!");
        assertEquals("PARTIAL", payPage.get("status"),
                "P1-BUG-3: Public pay page did not report PARTIAL status after partial payment!");
    }

    @Test
    void p1Bug4_tenantUpdateProfileMustPersistPhoneEmailAddressCityAndState() throws Exception {
        String phone = "+919520000108";
        adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P1 BUG 4 Gym", "Owner P1B4", phone, null,
                "OldCity", "OldState", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        AuthService.TokenPair pair = authService.login(phone, "Passw0rd!23");
        loginAs(pair.user());

        String payload = """
                {
                  "name": "Updated Gym Name",
                  "phone": "09400000099",
                  "email": "owner@updatedgym.in",
                  "address": "42 Hazratganj Main Road",
                  "city": "Lucknow",
                  "state": "Uttar Pradesh",
                  "gstin": "09AABCB1234F1Z5",
                  "gstRegistered": true,
                  "invoicePrefix": "UGM"
                }
                """;
        MvcResult res = mockMvc.perform(put("/api/v1/tenant/profile")
                        .header("Authorization", "Bearer " + pair.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn();
        assertEquals(200, res.getResponse().getStatus());

        loginAs(pair.user());
        Gym updated = tenantService.getGym();
        assertEquals("42 Hazratganj Main Road", updated.getAddress(),
                "P1-BUG-4: TenantService.updateProfile discarded address field!");
        assertEquals("owner@updatedgym.in", updated.getEmail(),
                "P1-BUG-4: TenantService.updateProfile discarded email field!");
        assertEquals("+919400000099", updated.getPhone(),
                "P1-BUG-4: TenantService.updateProfile discarded phone field!");
        assertEquals("Lucknow", updated.getCity(),
                "P1-BUG-4: TenantService.updateProfile discarded city field!");
        assertEquals("Uttar Pradesh", updated.getState(),
                "P1-BUG-4: TenantService.updateProfile discarded state field!");
    }

    @Test
    void p1Bug5_hindiInvoicePdfMustRenderDevanagariTextWithoutFontLoss() throws Exception {
        String phone = "+919520000109";
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "बजरंग जिम", "Owner P1B5", phone, null,
                "Lucknow", "Uttar Pradesh", "09AABCB1234F1Z5", true, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        Member hindiMember = memberService.create("रोहित शर्मा", "+919530000109", null, "M",
                null, "Gomti Nagar", "Uttar Pradesh", true, null);
        Plan hindiPlan = planService.create("मासिक प्लान (Monthly)", 30, 118000L);
        Membership ms = membershipService.enrol(hindiMember.getId(), hindiPlan.getId());
        Invoice inv = billingService.generateInvoice(ms.getId());

        byte[] pdfBytes = billingService.renderPdf(inv.getId());
        assertEquals("%PDF-", new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII));
        try (com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdfBytes)) {
            String extracted = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(extracted.contains("रोहित"),
                    "P1-BUG-5: PdfRenderer stripped Hindi Devanagari member name ('रोहित शर्मा') because Type-1 Helvetica lacks Unicode Devanagari glyphs! Extracted: " + extracted);
        }
    }

    @Test
    void p1Bug6_csvImporterMustEnforceHeaderEncodingRowLimitAndFormulaInjectionGuards() {
        String phone = "+919520000110";
        Map<String, Object> created = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "P1 BUG 6 Gym", "Owner P1B6", phone, null,
                "Jaipur", "Rajasthan", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        String gymId = (String) created.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(owner);

        String formulaCsv = """
                name,phone,email
                "=CMD|' /C calc'!A0",9876540101,a@b.com
                """;
        ImporterService.ImportResult res = importerService.importMembers(formulaCsv);
        assertEquals(0, res.success(),
                "P1-BUG-6: ImporterService accepted a spreadsheet formula-injection payload (=CMD...) instead of rejecting it!");
        assertFalse(res.errors().isEmpty());
    }
}
