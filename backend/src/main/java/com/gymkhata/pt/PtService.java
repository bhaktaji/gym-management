package com.gymkhata.pt;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.BillingService;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.CommissionRule;
import com.gymkhata.domain.MembershipDates;
import com.gymkhata.domain.PhoneUtils;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.model.PtPackage;
import com.gymkhata.pt.model.Trainer;
import com.gymkhata.pt.repo.PtEnrolmentRepository;
import com.gymkhata.pt.repo.PtPackageRepository;
import com.gymkhata.pt.repo.TrainerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PtService {

    private final TrainerRepository trainerRepository;
    private final PtPackageRepository ptPackageRepository;
    private final PtEnrolmentRepository ptEnrolmentRepository;
    private final MemberRepository memberRepository;
    private final GymRepository gymRepository;
    private final BillingService billingService;
    private final CommissionService commissionService;
    private final Clock clock;
    private final AuditService auditService;

    public PtService(TrainerRepository trainerRepository,
                     PtPackageRepository ptPackageRepository,
                     PtEnrolmentRepository ptEnrolmentRepository,
                     MemberRepository memberRepository,
                     GymRepository gymRepository,
                     BillingService billingService,
                     CommissionService commissionService,
                     Clock clock,
                     AuditService auditService) {
        this.trainerRepository = trainerRepository;
        this.ptPackageRepository = ptPackageRepository;
        this.ptEnrolmentRepository = ptEnrolmentRepository;
        this.memberRepository = memberRepository;
        this.gymRepository = gymRepository;
        this.billingService = billingService;
        this.commissionService = commissionService;
        this.clock = clock;
        this.auditService = auditService;
    }

    // --- Trainers ---

    public List<Trainer> listTrainers(Boolean activeOnly) {
        String gymId = SecurityUtils.currentGymId();
        if (Boolean.TRUE.equals(activeOnly)) {
            return trainerRepository.findByGymIdAndActiveOrderByNameAsc(gymId, true);
        }
        return trainerRepository.findByGymIdOrderByNameAsc(gymId);
    }

    public Trainer createTrainer(String name, String phone, Boolean active, CommissionRule defaultRule) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("INVALID_NAME", "Trainer name is required");
        }
        String e164 = PhoneUtils.normalize(phone);
        if (e164 == null) {
            throw BusinessException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
        }
        String gymId = SecurityUtils.currentGymId();
        Trainer t = new Trainer();
        t.setGymId(gymId);
        t.setName(name.trim());
        t.setPhone(e164);
        t.setActive(active == null || active);
        if (defaultRule != null) {
            defaultRule.setEffectiveFrom(clock.now());
            t.setDefaultCommissionRule(defaultRule);
        }
        t.setCreatedAt(clock.now());
        t.setUpdatedAt(clock.now());
        Trainer saved = trainerRepository.save(t);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "TRAINER_CREATED",
                "trainer", saved.getId(), saved.getName());
        return saved;
    }

    public Trainer updateTrainer(String id, String name, String phone, Boolean active, CommissionRule defaultRule) {
        String gymId = SecurityUtils.currentGymId();
        Trainer t = trainerRepository.findById(id)
                .filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("TRAINER_NOT_FOUND", "Trainer not found"));
        if (name != null && !name.isBlank()) {
            t.setName(name.trim());
        }
        if (phone != null && !phone.isBlank()) {
            String e164 = PhoneUtils.normalize(phone);
            if (e164 == null) {
                throw BusinessException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
            }
            t.setPhone(e164);
        }
        if (active != null) {
            t.setActive(active);
        }
        if (defaultRule != null) {
            defaultRule.setEffectiveFrom(clock.now());
            t.setDefaultCommissionRule(defaultRule);
        }
        t.setUpdatedAt(clock.now());
        Trainer saved = trainerRepository.save(t);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "TRAINER_UPDATED",
                "trainer", saved.getId(), saved.getName());
        return saved;
    }

    // --- PT Packages ---

    public List<PtPackage> listPackages(Boolean activeOnly) {
        String gymId = SecurityUtils.currentGymId();
        if (Boolean.TRUE.equals(activeOnly)) {
            return ptPackageRepository.findByGymIdAndActiveOrderByCreatedAtDesc(gymId, true);
        }
        return ptPackageRepository.findByGymIdOrderByCreatedAtDesc(gymId);
    }

    public PtPackage createPackage(String name, String packageType, int validityDays, Integer totalSessions,
                                   long pricePaise, String sacCode, CommissionRule overrideRule) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("INVALID_NAME", "Package name is required");
        }
        if (validityDays < 1) {
            throw BusinessException.badRequest("INVALID_VALIDITY", "Validity days must be >= 1");
        }
        if (pricePaise < 0) {
            throw BusinessException.badRequest("INVALID_PRICE", "Price must be >= 0");
        }
        String effType = (packageType != null && packageType.equalsIgnoreCase(PtPackage.TYPE_TIME_BASED))
                ? PtPackage.TYPE_TIME_BASED
                : PtPackage.TYPE_SESSION_BASED;
        if (PtPackage.TYPE_SESSION_BASED.equals(effType) && (totalSessions == null || totalSessions < 1)) {
            throw BusinessException.badRequest("INVALID_SESSIONS", "Session-based PT package requires totalSessions >= 1");
        }

        String gymId = SecurityUtils.currentGymId();
        Gym gym = gymRepository.findById(gymId).orElse(null);
        String defaultSac = (gym != null && gym.getSettings() != null)
                ? gym.getSettings().getPtSacCode()
                : "999723";

        PtPackage pkg = new PtPackage();
        pkg.setGymId(gymId);
        pkg.setName(name.trim());
        pkg.setPackageType(effType);
        pkg.setValidityDays(validityDays);
        pkg.setTotalSessions(PtPackage.TYPE_TIME_BASED.equals(effType) ? null : totalSessions);
        pkg.setPricePaise(pricePaise);
        pkg.setSacCode((sacCode != null && !sacCode.isBlank()) ? sacCode.trim() : defaultSac);
        if (overrideRule != null && overrideRule.getType() != null && !overrideRule.getType().isBlank()) {
            overrideRule.setEffectiveFrom(clock.now());
            pkg.setCommissionRuleOverride(overrideRule);
        }
        pkg.setActive(true);
        pkg.setCreatedAt(clock.now());
        pkg.setUpdatedAt(clock.now());
        PtPackage saved = ptPackageRepository.save(pkg);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "PT_PACKAGE_CREATED",
                "pt_package", saved.getId(), saved.getName());
        return saved;
    }

    public PtPackage updatePackage(String id, String name, String packageType, Integer validityDays,
                                   Integer totalSessions, Long pricePaise, String sacCode,
                                   CommissionRule overrideRule, Boolean active) {
        String gymId = SecurityUtils.currentGymId();
        PtPackage pkg = ptPackageRepository.findById(id)
                .filter(p -> gymId.equals(p.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("PT_PACKAGE_NOT_FOUND", "PT package not found"));
        if (name != null && !name.isBlank()) {
            pkg.setName(name.trim());
        }
        if (packageType != null && !packageType.isBlank()) {
            pkg.setPackageType(packageType.toUpperCase());
        }
        if (validityDays != null && validityDays >= 1) {
            pkg.setValidityDays(validityDays);
        }
        if (totalSessions != null) {
            pkg.setTotalSessions(totalSessions <= 0 ? null : totalSessions);
        }
        if (pricePaise != null && pricePaise >= 0) {
            pkg.setPricePaise(pricePaise);
        }
        if (sacCode != null && !sacCode.isBlank()) {
            pkg.setSacCode(sacCode.trim());
        }
        if (overrideRule != null) {
            overrideRule.setEffectiveFrom(clock.now());
            pkg.setCommissionRuleOverride(overrideRule);
        }
        if (active != null) {
            pkg.setActive(active);
        }
        pkg.setUpdatedAt(clock.now());
        return ptPackageRepository.save(pkg);
    }

    // --- Selling PT & Session Tracking ---

    public PtEnrolment sellPtPackage(String memberId, String ptPackageId, String trainerId, Long discountPaise) {
        String gymId = SecurityUtils.currentGymId();
        Member member = memberRepository.findById(memberId)
                .filter(m -> gymId.equals(m.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBER_NOT_FOUND", "Member not found"));
        PtPackage pkg = ptPackageRepository.findById(ptPackageId)
                .filter(p -> gymId.equals(p.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("PT_PACKAGE_NOT_FOUND", "PT package not found"));
        if (!pkg.isActive()) {
            throw BusinessException.badRequest("PT_PACKAGE_INACTIVE", "PT package is not active");
        }
        Trainer trainer = trainerRepository.findById(trainerId)
                .filter(t -> gymId.equals(t.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("TRAINER_NOT_FOUND", "Trainer not found"));
        if (!trainer.isActive()) {
            throw BusinessException.badRequest("TRAINER_INACTIVE", "Trainer is not active");
        }
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));

        long effDiscount = discountPaise != null ? Math.max(0L, discountPaise) : 0L;
        if (effDiscount > pkg.getPricePaise()) {
            throw BusinessException.badRequest("INVALID_DISCOUNT", "Discount cannot exceed package price");
        }

        LocalDate today = clock.today();
        LocalDate expiry = MembershipDates.expiryDate(today, pkg.getValidityDays());

        PtEnrolment enrolment = new PtEnrolment();
        enrolment.setGymId(gymId);
        enrolment.setMemberId(member.getId());
        enrolment.setMemberName(member.getName());
        enrolment.setPtPackageId(pkg.getId());
        enrolment.setPtPackageName(pkg.getName());
        enrolment.setPackageType(pkg.getPackageType());
        enrolment.setTrainerId(trainer.getId());
        enrolment.setTrainerName(trainer.getName());
        enrolment.setStartDate(today);
        enrolment.setExpiryDate(expiry);
        enrolment.setTotalSessions(pkg.getTotalSessions());
        enrolment.setRemainingSessions(pkg.getTotalSessions());
        enrolment.setDeliveredSessions(0);
        enrolment.setPricePaise(pkg.getPricePaise());
        enrolment.setDiscountPaise(effDiscount);
        enrolment.setSacCode(pkg.getSacCode());
        enrolment.setStatus(PtEnrolment.STATUS_ACTIVE);
        enrolment.setCreatedAt(clock.now());
        enrolment.setUpdatedAt(clock.now());

        PtEnrolment savedEnrolment = ptEnrolmentRepository.save(enrolment);

        Invoice invoice = billingService.generatePtInvoice(savedEnrolment, member, gym);
        savedEnrolment.setInvoiceId(invoice.getId());
        commissionService.snapshotRuleForEnrolment(savedEnrolment, pkg, trainer, gym, invoice.totalTaxablePaise());
        savedEnrolment = ptEnrolmentRepository.save(savedEnrolment);

        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "PT_SOLD",
                "pt_enrolment", savedEnrolment.getId(),
                "member=" + member.getName() + " pkg=" + pkg.getName() + " trainer=" + trainer.getName() + " inv=" + invoice.getInvoiceNumber());
        return savedEnrolment;
    }

    public List<PtEnrolment> listEnrolments() {
        return ptEnrolmentRepository.findByGymIdOrderByCreatedAtDesc(SecurityUtils.currentGymId());
    }

    public List<PtEnrolment> listEnrolmentsByMember(String memberId) {
        return ptEnrolmentRepository.findByGymIdAndMemberIdOrderByCreatedAtDesc(SecurityUtils.currentGymId(), memberId);
    }

    /**
     * Decrements a PT session (called by owner from MemberDetail/PT page or by member on self-service page).
     */
    public PtEnrolment markSessionDone(String enrolmentId) {
        String gymId = SecurityUtils.currentGymId();
        PtEnrolment e = ptEnrolmentRepository.findById(enrolmentId)
                .filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("PT_ENROLMENT_NOT_FOUND", "PT enrolment not found"));
        return decrementSessionInternal(e, SecurityUtils.currentUserId(), "GYM_OWNER");
    }

    public PtEnrolment markSessionDoneForMemberSelfService(String gymId, String memberId) {
        LocalDate today = clock.today();
        List<PtEnrolment> activeList = ptEnrolmentRepository.findByGymIdAndMemberIdOrderByCreatedAtDesc(gymId, memberId)
                .stream()
                .filter(e -> PtEnrolment.STATUS_ACTIVE.equals(e.getStatus()))
                .filter(e -> e.getExpiryDate() == null || !e.getExpiryDate().isBefore(today))
                .filter(e -> e.getRemainingSessions() != null && e.getRemainingSessions() > 0)
                .toList();
        if (activeList.isEmpty()) {
            throw BusinessException.badRequest("NO_ACTIVE_PT_SESSIONS", "No active session-based PT package with remaining sessions");
        }
        return decrementSessionInternal(activeList.getFirst(), memberId, "MEMBER_SELF");
    }

    private PtEnrolment decrementSessionInternal(PtEnrolment e, String actorId, String actorRole) {
        if (!PtEnrolment.STATUS_ACTIVE.equals(e.getStatus())) {
            throw BusinessException.badRequest("PT_NOT_ACTIVE", "PT package is not active");
        }
        LocalDate today = clock.today();
        if (e.getExpiryDate() != null && e.getExpiryDate().isBefore(today)) {
            e.setStatus(PtEnrolment.STATUS_EXPIRED);
            ptEnrolmentRepository.save(e);
            throw BusinessException.badRequest("PT_EXPIRED", "PT package validity has expired");
        }
        if (e.getRemainingSessions() == null || e.getRemainingSessions() <= 0) {
            throw BusinessException.badRequest("NO_SESSIONS_LEFT", "No remaining PT sessions to decrement");
        }
        int nextDelivered = e.getDeliveredSessions() + 1;
        int nextRemaining = e.getRemainingSessions() - 1;
        e.setDeliveredSessions(nextDelivered);
        e.setRemainingSessions(nextRemaining);
        if (nextRemaining == 0) {
            e.setStatus(PtEnrolment.STATUS_COMPLETED);
        }
        e.setUpdatedAt(clock.now());
        PtEnrolment saved = ptEnrolmentRepository.save(e);

        // Security rule: Member self check-in records session completion but does NOT unilaterally
        // trigger financial commission accrual under ON_SESSION_DELIVERED without owner/trainer confirmation.
        if (!"MEMBER_SELF".equals(actorRole)) {
            commissionService.onSessionDelivered(saved, nextDelivered);
        }

        auditService.log(e.getGymId(), actorId, actorRole, "PT_SESSION_DONE",
                "pt_enrolment", saved.getId(), "delivered=" + nextDelivered + " remaining=" + nextRemaining);
        return saved;
    }
}
