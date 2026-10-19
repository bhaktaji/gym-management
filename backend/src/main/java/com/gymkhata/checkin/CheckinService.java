package com.gymkhata.checkin;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.checkin.repo.CheckinRepository;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.CheckinRules;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CheckinService {

    private final CheckinRepository checkinRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final Clock clock;
    private final AuditService auditService;

    public CheckinService(CheckinRepository checkinRepository,
                          MemberRepository memberRepository,
                          MembershipRepository membershipRepository,
                          Clock clock,
                          AuditService auditService) {
        this.checkinRepository = checkinRepository;
        this.memberRepository = memberRepository;
        this.membershipRepository = membershipRepository;
        this.clock = clock;
        this.auditService = auditService;
    }

    public Checkin checkIn(String memberId) {
        String gymId = SecurityUtils.currentGymId();
        Member member = memberRepository.findById(memberId)
                .filter(m -> gymId.equals(m.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBER_NOT_FOUND", "Member not found"));
        Membership active = membershipRepository
                .findFirstByGymIdAndMemberIdAndStatusOrderByExpiryDateDesc(gymId, memberId, Membership.STATUS_ACTIVE);
        LocalDate today = clock.today();
        boolean alreadyIn = checkinRepository
                .findByGymIdAndMemberIdAndDateAndCheckOutAtIsNull(gymId, memberId, today)
                .isPresent();

        CheckinRules.Decision decision = CheckinRules.evaluate(new CheckinRules.Input(
                member.getStatus(),
                active == null ? null : active.getStatus(),
                active == null ? null : active.getExpiryDate(),
                today,
                alreadyIn));

        switch (decision) {
            case ALLOWED -> {
                Checkin c = new Checkin();
                c.setGymId(gymId);
                c.setMemberId(memberId);
                c.setMemberName(member.getName());
                c.setCheckInAt(clock.now());
                c.setDate(today);
                Checkin saved = checkinRepository.save(c);
                auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "CHECKIN", "member", memberId, member.getName());
                return saved;
            }
            case ALREADY_CHECKED_IN_TODAY ->
                    throw BusinessException.conflict("ALREADY_CHECKED_IN", "Member already checked in today");
            case MEMBER_INACTIVE ->
                    throw BusinessException.badRequest("MEMBER_INACTIVE", "Member is not active");
            case NO_ACTIVE_MEMBERSHIP ->
                    throw BusinessException.badRequest("NO_ACTIVE_MEMBERSHIP", "Member has no active membership");
            case MEMBERSHIP_EXPIRED ->
                    throw BusinessException.badRequest("MEMBERSHIP_EXPIRED", "Membership has expired");
            default -> throw BusinessException.badRequest("CHECKIN_DENIED", "Check-in not allowed");
        }
    }

    public Checkin checkOut(String checkinId) {
        String gymId = SecurityUtils.currentGymId();
        Checkin c = checkinRepository.findById(checkinId)
                .filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("CHECKIN_NOT_FOUND", "Check-in not found"));
        if (c.getCheckOutAt() != null) {
            throw BusinessException.badRequest("ALREADY_CHECKED_OUT", "Member already checked out");
        }
        c.setCheckOutAt(clock.now());
        return checkinRepository.save(c);
    }

    public List<Checkin> today() {
        return checkinRepository.findByGymIdAndDateOrderByCheckInAtDesc(SecurityUtils.currentGymId(), clock.today());
    }

    public List<Checkin> history(String memberId) {
        String gymId = SecurityUtils.currentGymId();
        return checkinRepository.findByGymIdAndMemberIdOrderByCheckInAtDesc(gymId, memberId);
    }
}
