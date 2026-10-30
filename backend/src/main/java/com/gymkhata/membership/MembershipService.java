package com.gymkhata.membership;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final PlanRepository planRepository;
    private final GymRepository gymRepository;
    private final Clock clock;
    private final AuditService auditService;

    public MembershipService(MembershipRepository membershipRepository,
                             MemberRepository memberRepository,
                             PlanRepository planRepository,
                             GymRepository gymRepository,
                             Clock clock,
                             AuditService auditService) {
        this.membershipRepository = membershipRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.gymRepository = gymRepository;
        this.clock = clock;
        this.auditService = auditService;
    }

    public Membership enrol(String memberId, String planId) {
        return enrol(memberId, planId, null, null, null);
    }

    /**
     * Enrol: creates a membership for a member from a plan.
     * - Early/on-time renewal starts from old expiry + 1 day.
     * - Late renewal starts from today (default) or old expiry + 1 day if gym lateRenewalPolicy == FROM_EXPIRY.
     * - Supports admission/joining fee, discount, and optional referral trainer.
     */
    public Membership enrol(String memberId, String planId, Long joiningFeePaise,
                            Long discountPaise, String referralTrainerId) {
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
        Gym gym = gymRepository.findById(gymId).orElse(null);
        String latePolicy = (gym != null && gym.getSettings() != null)
                ? gym.getSettings().getLateRenewalPolicy()
                : Gym.Settings.RENEWAL_FROM_TODAY;

        LocalDate today = clock.today();
        LocalDate startDate = today;

        Membership active = membershipRepository
                .findFirstByGymIdAndMemberIdAndStatusOrderByExpiryDateDesc(gymId, memberId, Membership.STATUS_ACTIVE);
        Membership latestAny = membershipRepository
                .findFirstByGymIdAndMemberIdOrderByExpiryDateDesc(gymId, memberId);

        if (active != null && !active.getExpiryDate().isBefore(today)) {
            startDate = active.getExpiryDate().plusDays(1);
        } else if (latestAny != null && latestAny.getExpiryDate() != null
                && latestAny.getExpiryDate().isBefore(today)
                && Gym.Settings.RENEWAL_FROM_EXPIRY.equalsIgnoreCase(latePolicy)) {
            startDate = latestAny.getExpiryDate().plusDays(1);
        }

        // invariant: one active membership per member — supersede the previous one
        if (active != null) {
            active.setStatus(Membership.STATUS_EXPIRED);
            membershipRepository.save(active);
        }
        LocalDate expiry = MembershipDates.expiryDate(startDate, plan.getDurationDays());

        long effJoiningFee = joiningFeePaise != null
                ? Math.max(0L, joiningFeePaise)
                : (latestAny == null ? Math.max(0L, plan.getJoiningFeePaise()) : 0L);
        long effDiscount = discountPaise != null ? Math.max(0L, discountPaise) : 0L;
        if (effDiscount > plan.getPricePaise() + effJoiningFee) {
            throw BusinessException.badRequest("INVALID_DISCOUNT", "Discount cannot exceed total plan + joining fee");
        }

        Membership m = new Membership();
        m.setGymId(gymId);
        m.setMemberId(memberId);
        m.setPlanId(plan.getId());
        m.setPlanName(plan.getName());
        m.setStartDate(startDate);
        m.setExpiryDate(expiry);
        m.setPricePaise(plan.getPricePaise());
        m.setJoiningFeePaise(effJoiningFee);
        m.setDiscountPaise(effDiscount);
        if (referralTrainerId != null && !referralTrainerId.isBlank()) {
            m.setReferralTrainerId(referralTrainerId.trim());
        }
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

    /**
     * Freezes/pauses a membership. If days > 0 is passed, immediately extends expiryDate by `days`
     * (or if days is null/0, transitions to FROZEN until unfreeze() is called).
     */
    public Membership freeze(String id, Integer days, String reason) {
        String gymId = SecurityUtils.currentGymId();
        Membership m = get(id);
        if (!Membership.STATUS_ACTIVE.equals(m.getStatus())) {
            throw BusinessException.badRequest("NOT_ACTIVE", "Only an active membership can be frozen");
        }
        if (days != null && days > 0) {
            m.setExpiryDate(m.getExpiryDate().plusDays(days));
            m.setTotalFrozenDays(m.getTotalFrozenDays() + days);
            m.setFreezeReason(reason);
            Membership saved = membershipRepository.save(m);
            auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "MEMBERSHIP_PAUSED_EXTENDED",
                    "membership", id, "days=" + days + " newExpiry=" + saved.getExpiryDate());
            return saved;
        }
        m.setStatus(Membership.STATUS_FROZEN);
        m.setFrozenAt(clock.today());
        m.setFreezeReason(reason);
        Membership saved = membershipRepository.save(m);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "MEMBERSHIP_FROZEN",
                "membership", id, reason);
        return saved;
    }

    /**
     * Unfreezes a FROZEN membership and extends expiryDate by the frozen days.
     */
    public Membership unfreeze(String id, Integer daysOverride) {
        String gymId = SecurityUtils.currentGymId();
        Membership m = get(id);
        if (!Membership.STATUS_FROZEN.equals(m.getStatus())) {
            throw BusinessException.badRequest("NOT_FROZEN", "Membership is not currently frozen");
        }
        int frozenDays;
        if (daysOverride != null && daysOverride > 0) {
            frozenDays = daysOverride;
        } else {
            LocalDate from = m.getFrozenAt() != null ? m.getFrozenAt() : clock.today();
            frozenDays = (int) Math.max(1L, ChronoUnit.DAYS.between(from, clock.today()));
        }
        m.setExpiryDate(m.getExpiryDate().plusDays(frozenDays));
        m.setTotalFrozenDays(m.getTotalFrozenDays() + frozenDays);
        m.setFrozenAt(null);
        m.setStatus(Membership.STATUS_ACTIVE);
        Membership saved = membershipRepository.save(m);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "MEMBERSHIP_UNFROZEN",
                "membership", id, "extendedDays=" + frozenDays + " newExpiry=" + saved.getExpiryDate());
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
