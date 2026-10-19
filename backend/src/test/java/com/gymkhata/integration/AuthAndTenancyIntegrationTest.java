package com.gymkhata.integration;

import com.gymkhata.AbstractIntegrationTest;
import com.gymkhata.admin.AdminService;
import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.AuthService;
import com.gymkhata.auth.SignupService;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.member.MemberService;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.plan.PlanService;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.plan.repo.PlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auth rules, the 1-owner-per-gym rule at the service level, and strict
 * tenant isolation (a gym owner can never see another gym's data).
 */
class AuthAndTenancyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminService adminService;
    @Autowired
    private AuthService authService;
    @Autowired
    private SignupService signupService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GymRepository gymRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private MemberService memberService;
    @Autowired
    private PlanService planService;

    private void loginAs(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void superAdminCreatesGymWithSingleOwner() {
        Map<String, Object> res = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Alpha Gym", "Owner One", "+919100000001", "owner@alpha.in",
                "Nashik", "Maharashtra", "27AAAAA0000A1Z5", true, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        String gymId = (String) res.get("gymId");
        assertNotNull(gymRepository.findById(gymId).orElse(null));
        assertTrue(userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).isPresent());
    }

    @Test
    void ownerLoginWorksWithPhoneInAnyFormat() {
        Map<String, Object> res = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Beta Gym", "Owner Two", "+919100000002", null,
                "Pune", "Maharashtra", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        authService.login("+919100000002", "Passw0rd!23");
        authService.login("919100000002", "Passw0rd!23");
        authService.login("09100000002", "Passw0rd!23");
    }

    @Test
    void duplicatePhoneIsRejected() {
        adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Gamma Gym", "Owner Three", "+919100000003", null,
                "Nagpur", "Maharashtra", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                        "Delta Gym", "Owner Four", "+919100000003", null,
                        "Surat", "Gujarat", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1))));
        assertEquals("PHONE_TAKEN", ex.getCode());
    }

    @Test
    void suspendedGymOwnerCannotLogIn() {
        Map<String, Object> res = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Epsilon Gym", "Owner Five", "+919100000004", null,
                "Indore", "Madhya Pradesh", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        String gymId = (String) res.get("gymId");

        adminService.setGymStatus(gymId, Gym.STATUS_SUSPENDED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login("+919100000004", "Passw0rd!23"));
        assertEquals("ACCOUNT_SUSPENDED", ex.getCode());
    }

    @Test
    void signupCreatesPendingGymWithOwner() {
        Map<String, Object> res = signupService.signup(new SignupService.SignupRequest(
                "Zeta Gym", "Owner Six", "+919100000006", "owner@zeta.in",
                "Jaipur", "Rajasthan", "Passw0rd!23"));

        String gymId = (String) res.get("gymId");
        assertEquals(Gym.STATUS_PENDING, res.get("status"));
        assertEquals(Gym.STATUS_PENDING, gymRepository.findById(gymId).orElseThrow().getStatus());
        assertTrue(userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).isPresent());
    }

    @Test
    void refreshTokenRotatesAndOldTokenIsRejected() {
        Map<String, Object> res = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Eta Gym", "Owner Seven", "+919100000007", null,
                "Kochi", "Kerala", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        String gymId = (String) res.get("gymId");
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER).orElseThrow();

        AuthService.TokenPair first = authService.login("+919100000007", "Passw0rd!23");
        AuthService.TokenPair second = authService.refresh(first.refreshToken());
        assertNotEquals(first.refreshToken(), second.refreshToken());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.refresh(first.refreshToken()));
        assertEquals("REFRESH_REUSE_DETECTED", ex.getCode());
    }

    @Test
    void ownerCannotSeeAnotherGymsMembers() {
        // gym A
        Map<String, Object> a = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Theta Gym", "Owner Eight", "+919100000008", null,
                "Lucknow", "Uttar Pradesh", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        User ownerA = userRepository.findByGymIdAndRole((String) a.get("gymId"), User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(ownerA);
        memberService.create("Alpha Member", "+919200000001", null, null, null, null, null, false, null);
        Plan planA = planService.create("Plan A", 30, 100000);

        // gym B
        Map<String, Object> b = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Iota Gym", "Owner Nine", "+919100000009", null,
                "Bhopal", "Madhya Pradesh", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        User ownerB = userRepository.findByGymIdAndRole((String) b.get("gymId"), User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(ownerB);
        memberService.create("Beta Member", "+919200000002", null, null, null, null, null, false, null);
        Plan planB = planService.create("Plan B", 60, 200000);

        // owner B sees only gym B data
        assertEquals(1, memberService.list(null).size());
        assertEquals("Beta Member", memberService.list(null).get(0).getName());
        assertEquals(1, planService.list().size());
        assertEquals("Plan B", planService.list().get(0).getName());

        // owner A still sees only gym A data
        loginAs(ownerA);
        assertEquals(1, memberService.list(null).size());
        assertEquals("Alpha Member", memberService.list(null).get(0).getName());
        assertEquals("Plan A", planService.list().get(0).getName());
    }

    @Test
    void memberPhoneIsUniquePerGymOnly() {
        Map<String, Object> a = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Kappa Gym", "Owner Ten", "+919100000010", null,
                "Chennai", "Tamil Nadu", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        User ownerA = userRepository.findByGymIdAndRole((String) a.get("gymId"), User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(ownerA);
        memberService.create("Shared Phone", "+919300000001", null, null, null, null, null, false, null);

        // same phone in the same gym is rejected
        BusinessException ex = assertThrows(BusinessException.class,
                () -> memberService.create("Dup", "+919300000001", null, null, null, null, null, false, null));
        assertEquals("PHONE_TAKEN", ex.getCode());

        // same phone in a different gym is fine
        Map<String, Object> b = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                "Lambda Gym", "Owner Eleven", "+919100000011", null,
                "Goa", "Goa", null, false, "Passw0rd!23", LocalDate.now().plusMonths(1)));
        User ownerB = userRepository.findByGymIdAndRole((String) b.get("gymId"), User.ROLE_GYM_OWNER).orElseThrow();
        loginAs(ownerB);
        assertDoesNotThrow(() -> memberService.create("Other Gym Same Phone", "+919300000001",
                null, null, null, null, null, false, null));
    }
}
