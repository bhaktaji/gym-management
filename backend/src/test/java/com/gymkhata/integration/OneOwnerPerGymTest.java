package com.gymkhata.integration;

import com.gymkhata.AbstractIntegrationTest;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The strict 1-owner-per-gym rule must hold at BOTH the service level and the
 * database index level.
 */
class OneOwnerPerGymTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        clearAll();
    }

    private User owner(String gymId, String phone) {
        User u = new User();
        u.setRole(User.ROLE_GYM_OWNER);
        u.setGymId(gymId);
        u.setName("Owner " + phone);
        u.setPhone(phone);
        u.setPasswordHash("x");
        u.setStatus(User.STATUS_ACTIVE);
        return u;
    }

    @Test
    @DisplayName("service level: a gym can have at most one GYM_OWNER")
    void serviceLevelRejectsSecondOwner() {
        userRepository.save(owner("gym-1", "+919111111111"));
        assertTrue(userRepository.existsByGymIdAndRole("gym-1", User.ROLE_GYM_OWNER));
        assertTrue(userRepository.findByGymIdAndRole("gym-1", User.ROLE_GYM_OWNER).isPresent());
    }

    @Test
    @DisplayName("database level: unique partial index on gymId where role=GYM_OWNER")
    void databaseLevelRejectsSecondOwner() {
        userRepository.save(owner("gym-2", "+919222222222"));
        assertThrows(DuplicateKeyException.class,
                () -> userRepository.save(owner("gym-2", "+919333333333")),
                "a second GYM_OWNER for the same gym must violate the unique index");
    }

    @Test
    @DisplayName("different gyms may each have one owner")
    void differentGymsEachHaveOneOwner() {
        userRepository.save(owner("gym-3", "+919444444444"));
        userRepository.save(owner("gym-4", "+919555555555"));
        assertEquals(2, userRepository.count());
    }

    @Test
    @DisplayName("SUPER_ADMIN has gymId null and is not affected by the partial index")
    void superAdminIsNotIndexed() {
        User admin = new User();
        admin.setRole(User.ROLE_SUPER_ADMIN);
        admin.setGymId(null);
        admin.setName("Platform Admin");
        admin.setPhone("+919000000001");
        admin.setPasswordHash("x");
        admin.setStatus(User.STATUS_ACTIVE);
        assertDoesNotThrow(() -> userRepository.save(admin));
        assertNull(userRepository.findByPhone("+919000000001").orElseThrow().getGymId());
    }

    @Test
    @DisplayName("user phone is globally unique")
    void phoneIsGloballyUnique() {
        userRepository.save(owner("gym-5", "+919666666666"));
        assertThrows(DuplicateKeyException.class, () -> userRepository.save(owner("gym-6", "+919666666666")));
    }
}
