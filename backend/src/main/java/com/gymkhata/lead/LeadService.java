package com.gymkhata.lead;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.PhoneUtils;
import com.gymkhata.lead.model.Lead;
import com.gymkhata.lead.repo.LeadRepository;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class LeadService {

    private final LeadRepository leadRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final AuditService auditService;

    public LeadService(LeadRepository leadRepository, MemberRepository memberRepository,
                       Clock clock, AuditService auditService) {
        this.leadRepository = leadRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
        this.auditService = auditService;
    }

    public List<Lead> list(String status) {
        String gymId = SecurityUtils.currentGymId();
        if (status != null && !status.isBlank()) {
            return leadRepository.findByGymIdAndStatus(gymId, status);
        }
        return leadRepository.findByGymId(gymId);
    }

    public Lead create(String name, String phone, String source, String notes) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("INVALID_NAME", "Name is required");
        }
        String e164 = PhoneUtils.normalize(phone);
        if (e164 == null) {
            throw BusinessException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
        }
        String gymId = SecurityUtils.currentGymId();
        if (leadRepository.findByGymIdAndPhone(gymId, e164).isPresent()) {
            throw BusinessException.conflict("PHONE_TAKEN", "A lead with this phone already exists");
        }
        Lead lead = new Lead();
        lead.setGymId(gymId);
        lead.setName(name.trim());
        lead.setPhone(e164);
        lead.setSource(source);
        lead.setStatus(Lead.STATUS_NEW);
        lead.setNotes(notes);
        lead.setCreatedAt(clock.now());
        lead.setUpdatedAt(clock.now());
        return leadRepository.save(lead);
    }

    public Lead update(String id, String status, String notes) {
        String gymId = SecurityUtils.currentGymId();
        Lead lead = leadRepository.findById(id)
                .filter(l -> gymId.equals(l.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("LEAD_NOT_FOUND", "Lead not found"));
        if (status != null) {
            if (!Lead.STATUS_NEW.equals(status) && !Lead.STATUS_CONTACTED.equals(status)
                    && !Lead.STATUS_CONVERTED.equals(status) && !Lead.STATUS_LOST.equals(status)) {
                throw BusinessException.badRequest("INVALID_STATUS", "Invalid lead status");
            }
            lead.setStatus(status);
        }
        if (notes != null) {
            lead.setNotes(notes);
        }
        lead.setUpdatedAt(clock.now());
        return leadRepository.save(lead);
    }

    /** Convert a lead into a member (enrolment). */
    public Member convert(String leadId) {
        String gymId = SecurityUtils.currentGymId();
        Lead lead = leadRepository.findById(leadId)
                .filter(l -> gymId.equals(l.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("LEAD_NOT_FOUND", "Lead not found"));
        if (Lead.STATUS_CONVERTED.equals(lead.getStatus())) {
            throw BusinessException.badRequest("ALREADY_CONVERTED", "Lead already converted");
        }
        if (memberRepository.findByGymIdAndPhone(gymId, lead.getPhone()).isPresent()) {
            throw BusinessException.conflict("ALREADY_MEMBER", "This phone is already a member");
        }
        Member m = new Member();
        m.setGymId(gymId);
        m.setName(lead.getName());
        m.setPhone(lead.getPhone());
        m.setStatus(Member.STATUS_ACTIVE);
        m.setWhatsappOptIn(true);
        m.setWhatsappOptInAt(clock.now());
        m.setJoinedAt(clock.today());
        m.setNotes("Converted from lead");
        m.setCreatedAt(clock.now());
        m.setUpdatedAt(clock.now());
        Member saved = memberRepository.save(m);

        lead.setStatus(Lead.STATUS_CONVERTED);
        lead.setConvertedToMemberId(saved.getId());
        lead.setUpdatedAt(clock.now());
        leadRepository.save(lead);

        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "LEAD_CONVERTED", "lead", leadId, lead.getName());
        return saved;
    }

    public Lead get(String id) {
        String gymId = SecurityUtils.currentGymId();
        return leadRepository.findById(id)
                .filter(l -> gymId.equals(l.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("LEAD_NOT_FOUND", "Lead not found"));
    }
}
