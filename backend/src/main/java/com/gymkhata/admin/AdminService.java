package com.gymkhata.admin;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.AuthService;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.common.error.DuplicateKeyException;
import com.gymkhata.domain.InvoiceNumbering;
import com.gymkhata.domain.PhoneUtils;
import com.gymkhata.tenant.TenantStatusInterceptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.mongodb.client.model.CountOptions;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final MongoTemplate template;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final TenantStatusInterceptor tenantStatusInterceptor;
    private final Clock clock;
    private final long monthlyPricePaise;

    public AdminService(GymRepository gymRepository,
                        UserRepository userRepository,
                        MongoTemplate template,
                        PasswordEncoder passwordEncoder,
                        AuthService authService,
                        TenantStatusInterceptor tenantStatusInterceptor,
                        Clock clock,
                        @Value("${app.pricing.monthly-price-paise}") long monthlyPricePaise) {
        this.gymRepository = gymRepository;
        this.userRepository = userRepository;
        this.template = template;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.tenantStatusInterceptor = tenantStatusInterceptor;
        this.clock = clock;
        this.monthlyPricePaise = monthlyPricePaise;
    }

    public record GymSummary(String id, String name, String code, String ownerName, String ownerPhone,
                            String city, String state, boolean gstRegistered, long activeMembers,
                            String status, LocalDate subscriptionValidUntil, Instant lastLoginAt,
                            Instant createdAt) {
    }

    public List<GymSummary> listGyms() {
        List<GymSummary> out = new ArrayList<>();
        for (Gym gym : gymRepository.findAll()) {
            User owner = userRepository.findByGymIdAndRole(gym.getId(), User.ROLE_GYM_OWNER).orElse(null);
            long activeMembers = countActiveMembers(gym.getId());
            out.add(new GymSummary(
                    gym.getId(), gym.getName(), gym.getCode(),
                    gym.getOwnerName(),
                    owner == null ? null : PhoneUtils.mask(owner.getPhone()),
                    gym.getCity(), gym.getState(), gym.isGstRegistered(),
                    activeMembers, gym.getStatus(), gym.getSubscriptionValidUntil(),
                    owner == null ? null : owner.getLastLoginAt(),
                    gym.getCreatedAt()));
        }
        return out;
    }

    private long countActiveMembers(String gymId) {
        Document query = new Document("gymId", gymId).append("status", "ACTIVE");
        return template.getCollection("members").countDocuments(query, new CountOptions().limit(1000));
    }

    public record CreateGymRequest(String gymName, String ownerName, String phone, String email,
                                  String city, String state, String gstin, boolean gstRegistered,
                                  String password, LocalDate subscriptionValidUntil) {
    }

    /**
     * Creates the gym AND its single GYM_OWNER in one workflow. The partial unique
     * index on users(gymId) where role=GYM_OWNER is the DB-level backstop for the
     * strict 1-owner-per-gym rule; if the owner insert fails, the gym is removed
     * (compensating step) since we have no multi-document transactions.
     */
    public Map<String, Object> createGymWithOwner(CreateGymRequest req) {
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
        if (req.email() != null && !req.email().isBlank() && userRepository.findByEmail(req.email().toLowerCase().trim()).isPresent()) {
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
        gym.setGstin(req.gstin());
        gym.setGstRegistered(req.gstRegistered());
        gym.setInvoicePrefix(InvoiceNumbering.normalizePrefix("GK"));
        gym.setStatus(Gym.STATUS_ACTIVE);
        gym.setSubscriptionValidUntil(req.subscriptionValidUntil() != null
                ? req.subscriptionValidUntil()
                : clock.today().plusMonths(1));
        gym.setPlanPricePaise(monthlyPricePaise);
        gym.setCreatedAt(clock.now());
        gym.setUpdatedAt(clock.now());
        gym = gymRepository.save(gym);

        try {
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
            userRepository.save(owner);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // DB-level backstop hit: compensate by removing the gym
            gymRepository.deleteById(gym.getId());
            throw new DuplicateKeyException("A gym already has an owner account");
        }
        return Map.of("gymId", gym.getId(), "code", code, "status", "created");
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

    public Map<String, Object> updateOwnerContact(String gymId, String phone, String email) {
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER)
                .orElseThrow(() -> BusinessException.notFound("OWNER_NOT_FOUND", "Gym has no owner account"));
        if (phone != null && !phone.isBlank()) {
            String e164 = PhoneUtils.normalize(phone);
            if (e164 == null) {
                throw BusinessException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
            }
            if (!e164.equals(owner.getPhone()) && userRepository.findByPhone(e164).isPresent()) {
                throw BusinessException.conflict("PHONE_TAKEN", "This phone is already registered");
            }
            owner.setPhone(e164);
            gym.setPhone(e164);
        }
        if (email != null && !email.isBlank()) {
            String normalized = email.toLowerCase().trim();
            if (!normalized.equals(owner.getEmail()) && userRepository.findByEmail(normalized).isPresent()) {
                throw BusinessException.conflict("EMAIL_TAKEN", "This email is already registered");
            }
            owner.setEmail(normalized);
            gym.setEmail(normalized);
        }
        owner.setUpdatedAt(clock.now());
        gym.setUpdatedAt(clock.now());
        userRepository.save(owner);
        gymRepository.save(gym);
        tenantStatusInterceptor.evict(gymId);
        return Map.of("status", "updated");
    }

    public Map<String, Object> triggerPasswordReset(String gymId) {
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));
        User owner = userRepository.findByGymIdAndRole(gymId, User.ROLE_GYM_OWNER)
                .orElseThrow(() -> BusinessException.notFound("OWNER_NOT_FOUND", "Gym has no owner account"));
        String token = authService.createPasswordResetToken(owner.getPhone());
        if (token == null) {
            throw BusinessException.notFound("OWNER_NOT_FOUND", "Gym has no owner account");
        }
        return Map.of("status", "reset_token_issued", "resetToken", token);
    }

    public Map<String, Object> setGymStatus(String gymId, String status) {
        if (!Gym.STATUS_ACTIVE.equals(status) && !Gym.STATUS_SUSPENDED.equals(status)) {
            throw BusinessException.badRequest("INVALID_STATUS", "Status must be ACTIVE or SUSPENDED");
        }
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));
        gym.setStatus(status);
        gym.setUpdatedAt(clock.now());
        gymRepository.save(gym);
        tenantStatusInterceptor.evict(gymId);
        return Map.of("status", "updated", "gymStatus", status);
    }

    public Map<String, Object> setSubscription(String gymId, LocalDate validUntil) {
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));
        gym.setSubscriptionValidUntil(validUntil);
        gym.setUpdatedAt(clock.now());
        gymRepository.save(gym);
        return Map.of("status", "updated", "subscriptionValidUntil", validUntil.toString());
    }
}
