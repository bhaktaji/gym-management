package com.gymkhata.pt;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.pdf.PdfRenderer;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.CommissionCalculator;
import com.gymkhata.domain.CommissionRule;
import com.gymkhata.domain.Money;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.pt.model.CommissionEntry;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.model.PtPackage;
import com.gymkhata.pt.model.Trainer;
import com.gymkhata.pt.repo.CommissionEntryRepository;
import com.gymkhata.pt.repo.PtEnrolmentRepository;
import com.gymkhata.pt.repo.PtPackageRepository;
import com.gymkhata.pt.repo.TrainerRepository;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommissionService {

    private final CommissionEntryRepository commissionEntryRepository;
    private final PtEnrolmentRepository ptEnrolmentRepository;
    private final PtPackageRepository ptPackageRepository;
    private final TrainerRepository trainerRepository;
    private final MembershipRepository membershipRepository;
    private final GymRepository gymRepository;
    private final PdfRenderer pdfRenderer;
    private final Clock clock;
    private final AuditService auditService;

    public CommissionService(CommissionEntryRepository commissionEntryRepository,
                             PtEnrolmentRepository ptEnrolmentRepository,
                             PtPackageRepository ptPackageRepository,
                             TrainerRepository trainerRepository,
                             MembershipRepository membershipRepository,
                             GymRepository gymRepository,
                             PdfRenderer pdfRenderer,
                             Clock clock,
                             AuditService auditService) {
        this.commissionEntryRepository = commissionEntryRepository;
        this.ptEnrolmentRepository = ptEnrolmentRepository;
        this.ptPackageRepository = ptPackageRepository;
        this.trainerRepository = trainerRepository;
        this.membershipRepository = membershipRepository;
        this.gymRepository = gymRepository;
        this.pdfRenderer = pdfRenderer;
        this.clock = clock;
        this.auditService = auditService;
    }

    public String currentMonth() {
        return YearMonth.from(clock.today()).toString();
    }

    /**
     * Resolves and snapshots the commission rule and total package commission for a new PT enrolment.
     */
    public void snapshotRuleForEnrolment(PtEnrolment enrolment, PtPackage pkg, Trainer trainer, Gym gym, long taxableValuePaise) {
        CommissionRule gymDefault = (gym != null && gym.getSettings() != null)
                ? gym.getSettings().getDefaultCommissionRule()
                : null;
        CommissionRule resolved = CommissionCalculator.resolveRule(
                pkg != null ? pkg.getCommissionRuleOverride() : null,
                trainer != null ? trainer.getDefaultCommissionRule() : null,
                gymDefault);
        enrolment.setResolvedRuleSnapshot(resolved);
        String month = currentMonth();
        long priorMonthlyTaxable = getMonthlyTaxableBase(enrolment.getGymId(), enrolment.getTrainerId(), month);
        long totalComm = CommissionCalculator.computeTotalPackageCommission(
                resolved, taxableValuePaise, enrolment.getTotalSessions(), priorMonthlyTaxable);
        enrolment.setTotalPackageCommissionPaise(totalComm);
    }

    public long getMonthlyTaxableBase(String gymId, String trainerId, String month) {
        return commissionEntryRepository.findByGymIdAndTrainerIdAndMonthOrderByCreatedAtAsc(gymId, trainerId, month)
                .stream()
                .filter(e -> CommissionEntry.TYPE_ACCRUAL.equals(e.getType()))
                .mapToLong(CommissionEntry::getTaxableBasePaise)
                .sum();
    }

    /**
     * Called when a payment (counter cash/card or UPI UTR confirmation) is confirmed against an invoice.
     */
    public void onPaymentConfirmed(Invoice invoice, Payment payment, long previouslyPaidPaise) {
        if (invoice == null || payment == null) {
            return;
        }
        String gymId = invoice.getGymId();
        Gym gym = gymRepository.findById(gymId).orElse(null);
        String trigger = (gym != null && gym.getSettings() != null)
                ? gym.getSettings().getCommissionTrigger()
                : Gym.Settings.TRIGGER_ON_PAYMENT;

        String month = currentMonth();

        // 1. Personal Training invoice commission (when trigger == ON_PAYMENT)
        PtEnrolment ptEnrolment = null;
        if (invoice.getPtEnrolmentId() != null) {
            ptEnrolment = ptEnrolmentRepository.findById(invoice.getPtEnrolmentId()).orElse(null);
        } else {
            ptEnrolment = ptEnrolmentRepository.findByGymIdAndInvoiceId(gymId, invoice.getId()).orElse(null);
        }

        if (ptEnrolment != null && Gym.Settings.TRIGGER_ON_PAYMENT.equalsIgnoreCase(trigger)) {
            CommissionRule rule = ptEnrolment.getResolvedRuleSnapshot();
            long totalComm = ptEnrolment.getTotalPackageCommissionPaise();
            if (rule == null) {
                PtPackage pkg = ptEnrolment.getPtPackageId() == null ? null
                        : ptPackageRepository.findById(ptEnrolment.getPtPackageId()).orElse(null);
                Trainer tr = ptEnrolment.getTrainerId() == null ? null
                        : trainerRepository.findById(ptEnrolment.getTrainerId()).orElse(null);
                snapshotRuleForEnrolment(ptEnrolment, pkg, tr, gym, invoice.totalTaxablePaise());
                ptEnrolmentRepository.save(ptEnrolment);
                rule = ptEnrolment.getResolvedRuleSnapshot();
                totalComm = ptEnrolment.getTotalPackageCommissionPaise();
            }
            if (rule != null && totalComm > 0) {
                long accrual = CommissionCalculator.computeProportionalPaymentAccrual(
                        totalComm, invoice.getTotalPaise(), previouslyPaidPaise, payment.getAmountPaise());
                if (accrual > 0) {
                    long proRataTaxable = CommissionCalculator.proRataHalfUp(
                            invoice.totalTaxablePaise(), payment.getAmountPaise(), Math.max(1L, invoice.getTotalPaise()));
                    CommissionEntry entry = new CommissionEntry();
                    entry.setGymId(gymId);
                    entry.setTrainerId(ptEnrolment.getTrainerId());
                    entry.setTrainerName(ptEnrolment.getTrainerName());
                    entry.setMemberId(ptEnrolment.getMemberId());
                    entry.setPtEnrolmentId(ptEnrolment.getId());
                    entry.setInvoiceId(invoice.getId());
                    entry.setPaymentId(payment.getId());
                    entry.setType(CommissionEntry.TYPE_ACCRUAL);
                    entry.setTriggerType(Gym.Settings.TRIGGER_ON_PAYMENT);
                    entry.setAmountPaise(accrual);
                    entry.setTaxableBasePaise(proRataTaxable);
                    entry.setMonth(month);
                    entry.setRuleSnapshot(rule.copyWithSource(rule.getSource()));
                    entry.setReason("PT payment accrual (" + invoice.getInvoiceNumber() + ")");
                    entry.setCreatedAt(clock.now());
                    commissionEntryRepository.save(entry);
                }
            }
            return;
        }

        // 2. Optional Membership Referral Commission (when enabled on gym settings)
        if (invoice.getMembershipId() != null && gym != null && gym.getSettings() != null
                && gym.getSettings().isMembershipReferralCommissionEnabled()) {
            Membership m = membershipRepository.findById(invoice.getMembershipId()).orElse(null);
            String trainerId = m != null && m.getReferralTrainerId() != null
                    ? m.getReferralTrainerId()
                    : invoice.getTrainerId();
            if (trainerId != null && !trainerId.isBlank()) {
                Trainer trainer = trainerRepository.findById(trainerId).orElse(null);
                if (trainer != null) {
                    CommissionRule rule = CommissionCalculator.resolveRule(
                            null, trainer.getDefaultCommissionRule(), gym.getSettings().getDefaultCommissionRule());
                    if (rule != null) {
                        long priorTaxable = getMonthlyTaxableBase(gymId, trainerId, month);
                        long totalComm = CommissionCalculator.computeTotalPackageCommission(
                                rule, invoice.totalTaxablePaise(), null, priorTaxable);
                        long accrual = CommissionCalculator.computeProportionalPaymentAccrual(
                                totalComm, invoice.getTotalPaise(), previouslyPaidPaise, payment.getAmountPaise());
                        if (accrual > 0) {
                            CommissionEntry entry = new CommissionEntry();
                            entry.setGymId(gymId);
                            entry.setTrainerId(trainer.getId());
                            entry.setTrainerName(trainer.getName());
                            entry.setMemberId(invoice.getMemberId());
                            entry.setMembershipId(invoice.getMembershipId());
                            entry.setInvoiceId(invoice.getId());
                            entry.setPaymentId(payment.getId());
                            entry.setType(CommissionEntry.TYPE_ACCRUAL);
                            entry.setTriggerType("REFERRAL");
                            entry.setAmountPaise(accrual);
                            entry.setTaxableBasePaise(invoice.totalTaxablePaise());
                            entry.setMonth(month);
                            entry.setRuleSnapshot(rule.copyWithSource(rule.getSource()));
                            entry.setReason("Membership referral accrual (" + invoice.getInvoiceNumber() + ")");
                            entry.setCreatedAt(clock.now());
                            commissionEntryRepository.save(entry);
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when a PT session is delivered (decremented). Accrues commission if gym trigger == ON_SESSION_DELIVERED.
     */
    public CommissionEntry onSessionDelivered(PtEnrolment enrolment, int sessionIndex) {
        Gym gym = gymRepository.findById(enrolment.getGymId()).orElse(null);
        String trigger = (gym != null && gym.getSettings() != null)
                ? gym.getSettings().getCommissionTrigger()
                : Gym.Settings.TRIGGER_ON_PAYMENT;
        if (!Gym.Settings.TRIGGER_ON_SESSION.equalsIgnoreCase(trigger)) {
            return null;
        }
        CommissionRule rule = enrolment.getResolvedRuleSnapshot();
        if (rule == null) {
            return null;
        }
        int totalSessions = enrolment.getTotalSessions() != null && enrolment.getTotalSessions() > 0
                ? enrolment.getTotalSessions()
                : 1;
        long accrual = CommissionCalculator.computeSessionDeliveredAccrual(
                rule, enrolment.getTotalPackageCommissionPaise(), totalSessions, sessionIndex);
        if (accrual <= 0) {
            return null;
        }
        CommissionEntry entry = new CommissionEntry();
        entry.setGymId(enrolment.getGymId());
        entry.setTrainerId(enrolment.getTrainerId());
        entry.setTrainerName(enrolment.getTrainerName());
        entry.setMemberId(enrolment.getMemberId());
        entry.setPtEnrolmentId(enrolment.getId());
        entry.setInvoiceId(enrolment.getInvoiceId());
        entry.setType(CommissionEntry.TYPE_ACCRUAL);
        entry.setTriggerType(Gym.Settings.TRIGGER_ON_SESSION);
        entry.setAmountPaise(accrual);
        entry.setMonth(currentMonth());
        entry.setRuleSnapshot(rule.copyWithSource(rule.getSource()));
        entry.setReason("PT session #" + sessionIndex + " delivered (" + enrolment.getPtPackageName() + ")");
        entry.setCreatedAt(clock.now());
        return commissionEntryRepository.save(entry);
    }

    /**
     * Creates a reversing CommissionEntry when a credit note is issued against an invoice.
     */
    public CommissionEntry reverseOnCreditNote(Invoice originalInvoice, Invoice creditNote) {
        if (originalInvoice == null || creditNote == null) {
            return null;
        }
        String gymId = originalInvoice.getGymId();
        List<CommissionEntry> priorEntries = commissionEntryRepository
                .findByGymIdAndInvoiceIdOrderByCreatedAtAsc(gymId, originalInvoice.getId());
        long netAccrued = priorEntries.stream()
                .filter(e -> CommissionEntry.TYPE_ACCRUAL.equals(e.getType()) || CommissionEntry.TYPE_REVERSAL.equals(e.getType()))
                .mapToLong(CommissionEntry::getAmountPaise)
                .sum();
        if (netAccrued <= 0) {
            return null;
        }
        CommissionEntry first = priorEntries.getFirst();
        CommissionEntry rev = new CommissionEntry();
        rev.setGymId(gymId);
        rev.setTrainerId(first.getTrainerId());
        rev.setTrainerName(first.getTrainerName());
        rev.setMemberId(originalInvoice.getMemberId());
        rev.setPtEnrolmentId(first.getPtEnrolmentId());
        rev.setMembershipId(first.getMembershipId());
        rev.setInvoiceId(originalInvoice.getId());
        rev.setCreditNoteId(creditNote.getId());
        rev.setType(CommissionEntry.TYPE_REVERSAL);
        rev.setTriggerType(first.getTriggerType());
        rev.setAmountPaise(-netAccrued);
        rev.setTaxableBasePaise(-first.getTaxableBasePaise());
        rev.setMonth(currentMonth());
        rev.setRuleSnapshot(first.getRuleSnapshot());
        rev.setReason("Credit note reversal (" + creditNote.getInvoiceNumber() + " for " + originalInvoice.getInvoiceNumber() + ")");
        rev.setCreatedAt(clock.now());
        return commissionEntryRepository.save(rev);
    }

    public CommissionEntry addAdjustment(String trainerId, String month, long amountPaise, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("REASON_REQUIRED", "Adjustment reason is required");
        }
        if (amountPaise == 0) {
            throw BusinessException.badRequest("INVALID_AMOUNT", "Adjustment amount must be non-zero");
        }
        String gymId = SecurityUtils.currentGymId();
        Trainer trainer = trainerRepository.findById(trainerId)
                .filter(t -> gymId.equals(t.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("TRAINER_NOT_FOUND", "Trainer not found"));
        String effMonth = (month == null || month.isBlank()) ? currentMonth() : month.trim();

        CommissionEntry adj = new CommissionEntry();
        adj.setGymId(gymId);
        adj.setTrainerId(trainer.getId());
        adj.setTrainerName(trainer.getName());
        adj.setType(CommissionEntry.TYPE_ADJUSTMENT);
        adj.setTriggerType("MANUAL");
        adj.setAmountPaise(amountPaise);
        adj.setMonth(effMonth);
        adj.setReason(reason.trim());
        adj.setCreatedAt(clock.now());
        CommissionEntry saved = commissionEntryRepository.save(adj);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "COMMISSION_ADJUSTED",
                "trainer", trainerId, "month=" + effMonth + " amount=" + amountPaise + " reason=" + reason);
        return saved;
    }

    public CommissionEntry markPayoutPaid(String trainerId, String month, long amountPaise,
                                          String payoutMode, LocalDate payoutDate, String notes) {
        if (amountPaise <= 0) {
            throw BusinessException.badRequest("INVALID_AMOUNT", "Payout amount must be > 0");
        }
        if (payoutMode == null || payoutMode.isBlank()) {
            throw BusinessException.badRequest("INVALID_MODE", "Payout mode (CASH, UPI, BANK_TRANSFER) is required");
        }
        String gymId = SecurityUtils.currentGymId();
        Trainer trainer = trainerRepository.findById(trainerId)
                .filter(t -> gymId.equals(t.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("TRAINER_NOT_FOUND", "Trainer not found"));
        String effMonth = (month == null || month.isBlank()) ? currentMonth() : month.trim();

        CommissionEntry payout = new CommissionEntry();
        payout.setGymId(gymId);
        payout.setTrainerId(trainer.getId());
        payout.setTrainerName(trainer.getName());
        payout.setType(CommissionEntry.TYPE_PAYOUT);
        payout.setTriggerType("PAYOUT");
        payout.setAmountPaise(amountPaise);
        payout.setMonth(effMonth);
        payout.setPayoutMode(payoutMode.trim().toUpperCase());
        payout.setPayoutDate(payoutDate != null ? payoutDate : clock.today());
        payout.setReason(notes != null && !notes.isBlank() ? notes.trim() : ("Payout via " + payoutMode.trim().toUpperCase()));
        payout.setCreatedAt(clock.now());
        CommissionEntry saved = commissionEntryRepository.save(payout);
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "COMMISSION_PAYOUT_PAID",
                "trainer", trainerId, "month=" + effMonth + " amount=" + amountPaise + " mode=" + payoutMode);
        return saved;
    }

    public Map<String, Object> getStatement(String trainerId, String month) {
        String gymId = SecurityUtils.currentGymId();
        Trainer trainer = trainerRepository.findById(trainerId)
                .filter(t -> gymId.equals(t.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("TRAINER_NOT_FOUND", "Trainer not found"));
        Gym gym = gymRepository.findById(gymId).orElse(null);
        String effMonth = (month == null || month.isBlank()) ? currentMonth() : month.trim();

        List<CommissionEntry> entries = commissionEntryRepository
                .findByGymIdAndTrainerIdAndMonthOrderByCreatedAtAsc(gymId, trainerId, effMonth);

        long accruedPaise = 0L;
        long reversalsPaise = 0L;
        long adjustmentsPaise = 0L;
        long paidPaise = 0L;

        for (CommissionEntry e : entries) {
            switch (e.getType()) {
                case CommissionEntry.TYPE_ACCRUAL -> accruedPaise += e.getAmountPaise();
                case CommissionEntry.TYPE_REVERSAL -> reversalsPaise += e.getAmountPaise(); // negative
                case CommissionEntry.TYPE_ADJUSTMENT -> adjustmentsPaise += e.getAmountPaise(); // signed
                case CommissionEntry.TYPE_PAYOUT -> paidPaise += e.getAmountPaise(); // positive
                default -> {
                }
            }
        }
        long netPayablePaise = accruedPaise + reversalsPaise + adjustmentsPaise;
        long balanceDuePaise = netPayablePaise - paidPaise;

        String gymName = gym != null ? gym.getName() : "GymKhata";
        String shareText = String.format(
                "Commission Statement (%s) - %s at %s: Accrued ₹%s | Reversals ₹%s | Adjustments ₹%s | Paid ₹%s | Balance Due ₹%s",
                effMonth, trainer.getName(), gymName,
                Money.formatIndian(accruedPaise),
                Money.formatIndian(reversalsPaise),
                Money.formatIndian(adjustmentsPaise),
                Money.formatIndian(paidPaise),
                Money.formatIndian(balanceDuePaise));

        String waLink = trainer.getPhone() != null
                ? "https://wa.me/" + trainer.getPhone().replace("+", "") + "?text=" + URLEncoder.encode(shareText, StandardCharsets.UTF_8)
                : "";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trainerId", trainer.getId());
        out.put("trainerName", trainer.getName());
        out.put("trainerPhone", trainer.getPhone());
        out.put("month", effMonth);
        out.put("accruedPaise", accruedPaise);
        out.put("reversalsPaise", reversalsPaise);
        out.put("adjustmentsPaise", adjustmentsPaise);
        out.put("netPayablePaise", netPayablePaise);
        out.put("paidPaise", paidPaise);
        out.put("balanceDuePaise", balanceDuePaise);
        out.put("entries", entries);
        out.put("whatsappShareText", shareText);
        out.put("waLink", waLink);
        return out;
    }

    public String exportStatementCsv(String trainerId, String month) {
        Map<String, Object> st = getStatement(trainerId, month);
        @SuppressWarnings("unchecked")
        List<CommissionEntry> entries = (List<CommissionEntry>) st.get("entries");
        StringBuilder sb = new StringBuilder();
        sb.append("Date,Type,Trigger,RuleType,AmountPaise,AmountRupees,PayoutMode,Reason\n");
        for (CommissionEntry e : entries) {
            String date = e.getCreatedAt() == null ? "" : e.getCreatedAt().toString();
            String ruleType = e.getRuleSnapshot() != null ? e.getRuleSnapshot().getType() : "";
            String mode = e.getPayoutMode() == null ? "" : e.getPayoutMode();
            String reason = e.getReason() == null ? "" : e.getReason().replace("\"", "\"\"");
            sb.append(String.format("%s,%s,%s,%s,%d,%s,%s,\"%s\"\n",
                    date, e.getType(), e.getTriggerType() == null ? "" : e.getTriggerType(),
                    ruleType, e.getAmountPaise(), Money.formatIndian(e.getAmountPaise()), mode, reason));
        }
        sb.append(String.format("SUMMARY,ACCRUED,,,%d,%s,,\"Total Accrued\"\n",
                (Long) st.get("accruedPaise"), Money.formatIndian((Long) st.get("accruedPaise"))));
        sb.append(String.format("SUMMARY,REVERSALS,,,%d,%s,,\"Total Reversals\"\n",
                (Long) st.get("reversalsPaise"), Money.formatIndian((Long) st.get("reversalsPaise"))));
        sb.append(String.format("SUMMARY,ADJUSTMENTS,,,%d,%s,,\"Total Adjustments\"\n",
                (Long) st.get("adjustmentsPaise"), Money.formatIndian((Long) st.get("adjustmentsPaise"))));
        sb.append(String.format("SUMMARY,PAID,,,%d,%s,,\"Total Paid\"\n",
                (Long) st.get("paidPaise"), Money.formatIndian((Long) st.get("paidPaise"))));
        sb.append(String.format("SUMMARY,BALANCE_DUE,,,%d,%s,,\"Balance Due\"\n",
                (Long) st.get("balanceDuePaise"), Money.formatIndian((Long) st.get("balanceDuePaise"))));
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public byte[] exportStatementPdf(String trainerId, String month) {
        Map<String, Object> st = getStatement(trainerId, month);
        Gym gym = gymRepository.findById(SecurityUtils.currentGymId()).orElse(null);
        return pdfRenderer.renderCommissionStatement(
                gym,
                (String) st.get("trainerName"),
                (String) st.get("month"),
                (Long) st.get("accruedPaise"),
                (Long) st.get("reversalsPaise"),
                (Long) st.get("adjustmentsPaise"),
                (Long) st.get("paidPaise"),
                (Long) st.get("balanceDuePaise"),
                (List<CommissionEntry>) st.get("entries"));
    }
}
