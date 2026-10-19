package com.gymkhata.member;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.PhoneUtils;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final Clock clock;
    private final AuditService auditService;

    public MemberService(MemberRepository memberRepository,
                         MembershipRepository membershipRepository,
                         Clock clock,
                         AuditService auditService) {
        this.memberRepository = memberRepository;
        this.membershipRepository = membershipRepository;
        this.clock = clock;
        this.auditService = auditService;
    }

    public List<Member> list(String status) {
        String gymId = SecurityUtils.currentGymId();
        if (status != null && !status.isBlank()) {
            return memberRepository.findByGymIdAndStatus(gymId, status);
        }
        return memberRepository.findByGymId(gymId);
    }

    public Member create(String name, String phone, String email, String gender,
                         java.time.LocalDate dob, String address, String state,
                         boolean whatsappOptIn, String notes) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("INVALID_NAME", "Name is required");
        }
        String e164 = PhoneUtils.normalize(phone);
        if (e164 == null) {
            throw BusinessException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
        }
        String gymId = SecurityUtils.currentGymId();
        if (memberRepository.findByGymIdAndPhone(gymId, e164).isPresent()) {
            throw BusinessException.conflict("PHONE_TAKEN", "A member with this phone already exists in this gym");
        }
        Member m = new Member();
        m.setGymId(gymId);
        m.setName(name.trim());
        m.setPhone(e164);
        m.setEmail(email);
        m.setGender(gender);
        m.setDob(dob);
        m.setAddress(address);
        m.setState(state);
        m.setStatus(Member.STATUS_ACTIVE);
        m.setWhatsappOptIn(whatsappOptIn);
        m.setWhatsappOptInAt(whatsappOptIn ? clock.now() : null);
        m.setJoinedAt(clock.today());
        m.setNotes(notes);
        m.setCreatedAt(clock.now());
        m.setUpdatedAt(clock.now());
        Member saved = memberRepository.save(m);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "MEMBER_CREATED", "member",
                saved.getId(), "name=" + name + " phone=" + PhoneUtils.mask(e164));
        return saved;
    }

    public Member update(String id, String name, String phone, String email, String gender,
                         java.time.LocalDate dob, String address, String state,
                         Boolean whatsappOptIn, String notes) {
        String gymId = SecurityUtils.currentGymId();
        Member m = memberRepository.findById(id)
                .filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBER_NOT_FOUND", "Member not found"));
        if (name != null && !name.isBlank()) {
            m.setName(name.trim());
        }
        if (phone != null && !phone.isBlank()) {
            String e164 = PhoneUtils.normalize(phone);
            if (e164 == null) {
                throw BusinessException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
            }
            if (!e164.equals(m.getPhone())
                    && memberRepository.findByGymIdAndPhone(gymId, e164).isPresent()) {
                throw BusinessException.conflict("PHONE_TAKEN", "A member with this phone already exists in this gym");
            }
            m.setPhone(e164);
        }
        if (email != null) {
            m.setEmail(email.isBlank() ? null : email);
        }
        if (gender != null) {
            m.setGender(gender);
        }
        if (dob != null) {
            m.setDob(dob);
        }
        if (address != null) {
            m.setAddress(address);
        }
        if (state != null) {
            m.setState(state);
        }
        if (whatsappOptIn != null && whatsappOptIn != m.isWhatsappOptIn()) {
            m.setWhatsappOptIn(whatsappOptIn);
            m.setWhatsappOptInAt(whatsappOptIn ? clock.now() : null);
        }
        if (notes != null) {
            m.setNotes(notes);
        }
        m.setUpdatedAt(clock.now());
        return memberRepository.save(m);
    }

    public Member get(String id) {
        String gymId = SecurityUtils.currentGymId();
        return memberRepository.findById(id)
                .filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBER_NOT_FOUND", "Member not found"));
    }

    public Member setStatus(String id, String status) {
        if (!Member.STATUS_ACTIVE.equals(status) && !Member.STATUS_INACTIVE.equals(status)) {
            throw BusinessException.badRequest("INVALID_STATUS", "Status must be ACTIVE or INACTIVE");
        }
        Member m = get(id);
        m.setStatus(status);
        m.setUpdatedAt(clock.now());
        return memberRepository.save(m);
    }

    /**
     * DPDP: member data export (JSON) — everything we hold on the member.
     */
    public Map<String, Object> exportData(String id) {
        Member m = get(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("member", m);
        out.put("memberships", membershipRepository.findByGymIdAndMemberIdOrderByCreatedAtDesc(m.getGymId(), m.getId()));
        return out;
    }

    /**
     * DPDP erase/anonymise: member is anonymised but invoices and payments are
     * retained (they reference memberId, which is preserved).
     */
    public Member erase(String id) {
        String gymId = SecurityUtils.currentGymId();
        Member m = get(id);
        m.setName("Erased Member");
        m.setPhone(null);
        m.setEmail(null);
        m.setGender(null);
        m.setDob(null);
        m.setAddress(null);
        m.setState(null);
        m.setNotes(null);
        m.setWhatsappOptIn(false);
        m.setUpdatedAt(clock.now());
        Member saved = memberRepository.save(m);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "MEMBER_ERASED", "member", id, "anonymised");
        return saved;
    }
}
