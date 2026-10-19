package com.gymkhata.membership;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.MembershipDates;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.plan.model.Plan;
import com.gymkhata.plan.repo.PlanRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final Clock clock;
    private final AuditService auditService;

    public MembershipService(MembershipRepository membershipRepository,
                             MemberRepository memberRepository,
                             PlanRepository planRepository,
                             Clock clock,
                             AuditService auditService) {
        this.membershipRepository = membershipRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.clock = clock;
        this.auditService = auditService;
    }

    /**
     * Enrol: creates a membership for a member from a plan. If the member already
     * has an active membership, the new one starts after the current expiry (extension).
     */
    public Membership enrol(String memberId, String planId) {
        String gymId = SecurityUtils.currentGymId();
        Member member = memberRepository.findById(memberId)
                .filter(m -> gymId.equals(m.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBER_NOT_FOUND", "Member not found"));
        Plan plan = planRepository.findById(planId)
                .filter(p -> gymId.equals(p.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("PLAN_NOT_FOUND", "Plan not found"));
        if (!plan.isActive()) {
            throw BusinessException.badRequest("PLAN_INACTIVE", "Plan is not active");
        }
        LocalDate today = clock.today();
        LocalDate startDate = today;
        Membership active = membershipRepository
                .findFirstByGymIdAndMemberIdAndStatusOrderByExpiryDateDesc(gymId, memberId, Membership.STATUS_ACTIVE);
        if (active != null && !active.getExpiryDate().isBefore(today)) {
            startDate = active.getExpiryDate().plusDays(1);
        }
        // invariant: one active membership per member — supersede the previous one
        if (active != null) {
            active.setStatus(Membership.STATUS_EXPIRED);
            membershipRepository.save(active);
        }
        LocalDate expiry = MembershipDates.expiryDate(startDate, plan.getDurationDays());

        Membership m = new Membership();
        m.setGymId(gymId);
        m.setMemberId(memberId);
        m.setPlanId(plan.getId());
        m.setPlanName(plan.getName());
        m.setStartDate(startDate);
        m.setExpiryDate(expiry);
        m.setPricePaise(plan.getPricePaise());
        m.setStatus(Membership.STATUS_ACTIVE);
        m.setCreatedAt(clock.now());
        Membership saved = membershipRepository.save(m);

        member.setStatus(Member.STATUS_ACTIVE);
        member.setUpdatedAt(clock.now());
        memberRepository.save(member);

        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "MEMBERSHIP_CREATED", "membership",
                saved.getId(), "member=" + memberId + " plan=" + plan.getName());
        return saved;
    }

    public List<Membership> listByMember(String memberId) {
        String gymId = SecurityUtils.currentGymId();
        return membershipRepository.findByGymIdAndMemberIdOrderByCreatedAtDesc(gymId, memberId);
    }

    public List<Membership> listActive() {
        String gymId = SecurityUtils.currentGymId();
        return membershipRepository.findByGymIdAndStatus(gymId, Membership.STATUS_ACTIVE);
    }

    public Membership get(String id) {
        String gymId = SecurityUtils.currentGymId();
        return membershipRepository.findById(id)
                .filter(m -> gymId.equals(m.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBERSHIP_NOT_FOUND", "Membership not found"));
    }
}
