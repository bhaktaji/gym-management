package com.gymkhata.plan;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.plan.repo.PlanRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final Clock clock;
    private final AuditService auditService;

    public PlanService(PlanRepository planRepository, Clock clock, AuditService auditService) {
        this.planRepository = planRepository;
        this.clock = clock;
        this.auditService = auditService;
    }

    public List<Plan> list() {
        return planRepository.findByGymIdAndActive(SecurityUtils.currentGymId(), true);
    }

    public Plan create(String name, int durationDays, long pricePaise) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("INVALID_NAME", "Plan name is required");
        }
        if (durationDays < 1) {
            throw BusinessException.badRequest("INVALID_DURATION", "Duration must be at least 1 day");
        }
        if (pricePaise < 0) {
            throw BusinessException.badRequest("INVALID_PRICE", "Price must be >= 0");
        }
        String gymId = SecurityUtils.currentGymId();
        Plan plan = new Plan();
        plan.setGymId(gymId);
        plan.setName(name.trim());
        plan.setDurationDays(durationDays);
        plan.setPricePaise(pricePaise);
        plan.setActive(true);
        plan.setCreatedAt(clock.now());
        plan.setUpdatedAt(clock.now());
        Plan saved = planRepository.save(plan);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "PLAN_CREATED", "plan", saved.getId(), name);
        return saved;
    }

    public Plan update(String id, String name, Integer durationDays, Long pricePaise, Boolean active) {
        String gymId = SecurityUtils.currentGymId();
        Plan plan = planRepository.findById(id)
                .filter(p -> gymId.equals(p.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("PLAN_NOT_FOUND", "Plan not found"));
        if (name != null && !name.isBlank()) {
            plan.setName(name.trim());
        }
        if (durationDays != null) {
            if (durationDays < 1) {
                throw BusinessException.badRequest("INVALID_DURATION", "Duration must be at least 1 day");
            }
            plan.setDurationDays(durationDays);
        }
        if (pricePaise != null) {
            if (pricePaise < 0) {
                throw BusinessException.badRequest("INVALID_PRICE", "Price must be >= 0");
            }
            plan.setPricePaise(pricePaise);
        }
        if (active != null) {
            plan.setActive(active);
        }
        plan.setUpdatedAt(clock.now());
        return planRepository.save(plan);
    }

    public Plan get(String id) {
        String gymId = SecurityUtils.currentGymId();
        return planRepository.findById(id)
                .filter(p -> gymId.equals(p.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("PLAN_NOT_FOUND", "Plan not found"));
    }
}
