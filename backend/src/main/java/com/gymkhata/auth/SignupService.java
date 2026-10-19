package com.gymkhata.auth;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.InvoiceNumbering;
import com.gymkhata.domain.PhoneUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Self-signup: a new Gym Owner signs up on the public site. Creates the gym
 * (PENDING unless auto-activate is on) plus its single GYM_OWNER account.
 */
@Service
public class SignupService {

    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final boolean autoActivate;

    public SignupService(GymRepository gymRepository,
                         UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         Clock clock,
                         @Value("${app.signup.auto-activate}") boolean autoActivate) {
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.autoActivate = autoActivate;
    }

    public record SignupRequest(String gymName, String ownerName, String phone, String email,
                                String city, String state, String password) {
    }

    public Map<String, Object> signup(SignupRequest req) {
        String e164 = PhoneUtils.normalize(req.phone());
        if (e164 == null) {
            throw BusinessException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
        }
        if (req.password() == null || req.password().length() < 8) {
            throw BusinessException.badRequest("WEAK_PASSWORD", "Password must be at least 8 characters");
        }
        if (userRepository.findByPhone(e164).isPresent()) {
            throw BusinessException.conflict("PHONE_TAKEN", "This phone is already registered");
        }
        if (req.email() != null && !req.email().isBlank()
                && userRepository.findByEmail(req.email().toLowerCase().trim()).isPresent()) {
            throw BusinessException.conflict("EMAIL_TAKEN", "This email is already registered");
        }
        String code = generateGymCode(req.gymName());
        Gym gym = new Gym();
        gym.setName(req.gymName().trim());
        gym.setCode(code);
        gym.setOwnerName(req.ownerName().trim());
        gym.setPhone(e164);
        gym.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().toLowerCase().trim());
        gym.setCity(req.city());
        gym.setState(req.state());
        gym.setGstRegistered(false);
        gym.setInvoicePrefix(InvoiceNumbering.normalizePrefix("GK"));
        gym.setStatus(autoActivate ? Gym.STATUS_ACTIVE : Gym.STATUS_PENDING);
        gym.setSubscriptionValidUntil(clock.today().plusMonths(1));
        gym.setCreatedAt(clock.now());
        gym.setUpdatedAt(clock.now());
        gym = gymRepository.save(gym);

        User owner = new User();
        owner.setRole(User.ROLE_GYM_OWNER);
        owner.setGymId(gym.getId());
        owner.setName(req.ownerName().trim());
        owner.setPhone(e164);
        owner.setEmail(gym.getEmail());
        owner.setPasswordHash(passwordEncoder.encode(req.password()));
        owner.setStatus(User.STATUS_ACTIVE);
        owner.setCreatedAt(clock.now());
        owner.setUpdatedAt(clock.now());
        try {
            userRepository.save(owner);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            gymRepository.deleteById(gym.getId());
            throw BusinessException.conflict("PHONE_TAKEN", "This phone is already registered");
        }
        return Map.of("gymId", gym.getId(), "code", code, "status", gym.getStatus());
    }

    private String generateGymCode(String gymName) {
        String base = gymName.replaceAll("[^A-Za-z]", "").toUpperCase();
        if (base.length() < 3) {
            base = (base + "GYM").substring(0, 3);
        }
        String candidate = base.substring(0, 3);
        int suffix = 1;
        while (gymRepository.findByCode(candidate).isPresent()) {
            candidate = base.substring(0, 2) + (suffix++);
            if (suffix > 99) {
                candidate = "G" + (suffix % 10) + "X";
            }
        }
        return candidate;
    }
}
