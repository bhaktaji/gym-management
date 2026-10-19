package com.gymkhata.tenant;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.InvoiceNumbering;
import com.gymkhata.domain.UpiUtils;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.plan.repo.PlanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TenantService {

    private final GymRepository gymRepository;
    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final Clock clock;
    private final AuditService auditService;
    private final long monthlyPricePaise;

    public TenantService(GymRepository gymRepository,
                         MemberRepository memberRepository,
                         PlanRepository planRepository,
                         Clock clock,
                         AuditService auditService,
                         @Value("${app.pricing.monthly-price-paise}") long monthlyPricePaise) {
        this.gymRepository = gymRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.clock = clock;
        this.auditService = auditService;
        this.monthlyPricePaise = monthlyPricePaise;
    }

    public Gym getGym() {
        return gymRepository.findById(SecurityUtils.currentGymId())
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));
    }

    public Gym updateSettings(Gym.Settings settings) {
        Gym gym = getGym();
        if (settings.getUpiVpa() != null && !settings.getUpiVpa().isBlank()
                && !UpiUtils.isValidVpa(settings.getUpiVpa())) {
            throw BusinessException.badRequest("INVALID_VPA", "Enter a valid UPI VPA (e.g. name@okhdfcbank)");
        }
        gym.setSettings(settings);
        gym.setUpdatedAt(clock.now());
        Gym saved = gymRepository.save(gym);
        auditService.log(gym.getId(), SecurityUtils.currentUserId(), "GYM_OWNER", "SETTINGS_UPDATED", "gym", gym.getId(), null);
        return saved;
    }

    public Gym updateProfile(String name, String phone, String email, String address, String city,
                             String state, String gstin, Boolean gstRegistered, String invoicePrefix) {
        Gym gym = getGym();
        if (name != null && !name.isBlank()) {
            gym.setName(name.trim());
        }
        if (gstin != null) {
            gym.setGstin(gstin.isBlank() ? null : gstin.toUpperCase());
        }
        if (gstRegistered != null) {
            gym.setGstRegistered(gstRegistered);
        }
        if (invoicePrefix != null && !invoicePrefix.isBlank()) {
            gym.setInvoicePrefix(InvoiceNumbering.normalizePrefix(invoicePrefix));
        }
        gym.setUpdatedAt(clock.now());
        return gymRepository.save(gym);
    }

    /**
     * Plan & usage screen: the same flat price the public /pricing page shows,
     * alongside the gym's subscription status managed by the Super Admin.
     */
    public Map<String, Object> planUsage() {
        Gym gym = getGym();
        long memberCount = memberRepository.countByGymIdAndStatus(gym.getId(), Member.STATUS_ACTIVE);
        long planCount = planRepository.findByGymIdAndActive(gym.getId(), true).size();
        LocalDate today = clock.today();
        boolean active = gym.getSubscriptionValidUntil() != null
                && !gym.getSubscriptionValidUntil().isBefore(today);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pricePaise", gym.getPlanPricePaise() > 0 ? gym.getPlanPricePaise() : monthlyPricePaise);
        out.put("currency", "INR");
        out.put("perMemberFee", 0);
        out.put("perMessageFee", 0);
        out.put("subscriptionStatus", active ? "ACTIVE" : "EXPIRED");
        out.put("subscriptionValidUntil", gym.getSubscriptionValidUntil());
        out.put("gymStatus", gym.getStatus());
        out.put("usage", Map.of(
                "members", memberCount,
                "plans", planCount,
                "membersIncluded", "unlimited",
                "messagesIncluded", "unlimited"));
        return out;
    }
}
