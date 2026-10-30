package com.gymkhata.billing;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.model.Counter;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.pdf.PdfRenderer;
import com.gymkhata.billing.provider.PaymentProvider;
import com.gymkhata.billing.repo.InvoiceRepository;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.FinancialYear;
import com.gymkhata.domain.GstCalculator;
import com.gymkhata.domain.IndianNumberWords;
import com.gymkhata.domain.InvoiceNumbering;
import com.gymkhata.member.model.Member;
import com.gymkhata.member.repo.MemberRepository;
import com.gymkhata.membership.model.Membership;
import com.gymkhata.membership.repo.MembershipRepository;
import com.gymkhata.pt.CommissionService;
import com.gymkhata.pt.model.PtEnrolment;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingService {

    public static final String SERIES_INV = "INV";
    public static final String SERIES_CN = "CN";

    private final InvoiceRepository invoiceRepository;
    private final MembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final GymRepository gymRepository;
    private final MongoTemplate template;
    private final GstConfig gstConfig;
    private final PaymentProvider paymentProvider;
    private final PdfRenderer pdfRenderer;
    private final PaymentService paymentService;
    private final CommissionService commissionService;
    private final Clock clock;
    private final AuditService auditService;

    public BillingService(InvoiceRepository invoiceRepository,
                          MembershipRepository membershipRepository,
                          MemberRepository memberRepository,
                          GymRepository gymRepository,
                          MongoTemplate template,
                          GstConfig gstConfig,
                          PaymentProvider paymentProvider,
                          PdfRenderer pdfRenderer,
                          PaymentService paymentService,
                          CommissionService commissionService,
                          Clock clock,
                          AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.membershipRepository = membershipRepository;
        this.memberRepository = memberRepository;
        this.gymRepository = gymRepository;
        this.template = template;
        this.gstConfig = gstConfig;
        this.paymentProvider = paymentProvider;
        this.pdfRenderer = pdfRenderer;
        this.paymentService = paymentService;
        this.commissionService = commissionService;
        this.clock = clock;
        this.auditService = auditService;
    }

    /**
     * Generates the invoice for a membership (including optional joining fee and discount).
     * Takes a gapless number first; if the invoice insert fails, a VOID invoice is written
     * with that number so the series stays gapless and auditable.
     */
    public Invoice generateInvoice(String membershipId) {
        String gymId = SecurityUtils.currentGymId();
        Membership membership = membershipRepository.findById(membershipId)
                .filter(m -> gymId.equals(m.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBERSHIP_NOT_FOUND", "Membership not found"));
        Member member = memberRepository.findById(membership.getMemberId())
                .filter(m -> gymId.equals(m.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("MEMBER_NOT_FOUND", "Member not found"));
        Gym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));

        LocalDate today = clock.today();
        String fy = FinancialYear.fyOf(today);
        long seq = nextNumber(gymId, fy, SERIES_INV);
        String number = InvoiceNumbering.format(gym.getInvoicePrefix(), fy, seq);

        Invoice invoice = new Invoice();
        invoice.setGymId(gymId);
        invoice.setInvoiceNumber(number);
        invoice.setFinancialYear(fy);
        invoice.setMemberId(member.getId());
        invoice.setMembershipId(membership.getId());
        invoice.setTrainerId(membership.getReferralTrainerId());
        invoice.setJoiningFeePaise(membership.getJoiningFeePaise());
        invoice.setDiscountPaise(membership.getDiscountPaise());
        invoice.setSupplierState(gym.getState());
        invoice.setPlaceOfSupplyState(member.getState() != null ? member.getState() : gym.getState());
        invoice.setGstRegistered(gym.isGstRegistered());
        invoice.setGstin(gym.isGstRegistered() ? gym.getGstin() : null);
        invoice.setStatus(Invoice.STATUS_ISSUED);
        invoice.setCreatedAt(clock.now());
        invoice.setCreatedBy(SecurityUtils.currentUserId());
        invoice.setPayToken(UUID.randomUUID().toString().replace("-", ""));

        buildMembershipLines(invoice, membership, gym, today);

        try {
            return invoiceRepository.save(invoice);
        } catch (Exception e) {
            // keep the series gapless: burn the taken number as a VOID invoice
            writeVoid(gymId, number, fy, "insert failed: " + e.getMessage());
            throw BusinessException.conflict("INVOICE_NUMBER_CONFLICT",
                    "Could not allocate invoice number. Please retry.");
        }
    }

    /**
     * Generates the GST invoice for a Personal Training enrolment using the gym's GST settings
     * and the PT package's configurable SAC code.
     */
    public Invoice generatePtInvoice(PtEnrolment enrolment, Member member, Gym gym) {
        String gymId = gym.getId();
        LocalDate today = clock.today();
        String fy = FinancialYear.fyOf(today);
        long seq = nextNumber(gymId, fy, SERIES_INV);
        String number = InvoiceNumbering.format(gym.getInvoicePrefix(), fy, seq);

        Invoice invoice = new Invoice();
        invoice.setGymId(gymId);
        invoice.setInvoiceNumber(number);
        invoice.setFinancialYear(fy);
        invoice.setMemberId(member.getId());
        invoice.setPtEnrolmentId(enrolment.getId());
        invoice.setTrainerId(enrolment.getTrainerId());
        invoice.setDiscountPaise(enrolment.getDiscountPaise());
        invoice.setSupplierState(gym.getState());
        invoice.setPlaceOfSupplyState(member.getState() != null ? member.getState() : gym.getState());
        invoice.setGstRegistered(gym.isGstRegistered());
        invoice.setGstin(gym.isGstRegistered() ? gym.getGstin() : null);
        invoice.setStatus(Invoice.STATUS_ISSUED);
        invoice.setCreatedAt(clock.now());
        String createdBy = "system";
        try {
            createdBy = SecurityUtils.currentUserId();
        } catch (Exception ignored) {
        }
        invoice.setCreatedBy(createdBy);
        invoice.setPayToken(UUID.randomUUID().toString().replace("-", ""));

        long netTotal = Math.max(0L, enrolment.getPricePaise() - enrolment.getDiscountPaise());
        String sac = (enrolment.getSacCode() != null && !enrolment.getSacCode().isBlank())
                ? enrolment.getSacCode()
                : (gym.getSettings() != null ? gym.getSettings().getPtSacCode() : gstConfig.getSac());
        String desc = "Personal Training - " + enrolment.getPtPackageName() + " (Trainer: " + enrolment.getTrainerName() + ")";
        if (enrolment.getDiscountPaise() > 0) {
            desc += " [Less Discount]";
        }
        Invoice.Line line = createLine(desc, netTotal, sac, gym.isGstRegistered(), today,
                invoice.getSupplierState(), invoice.getPlaceOfSupplyState());
        invoice.setLines(List.of(line));
        invoice.setTotalPaise(netTotal);
        invoice.setAmountInWords(IndianNumberWords.amountInWords(netTotal));
        invoice.setType(gym.isGstRegistered() ? Invoice.TYPE_TAX_INVOICE : Invoice.TYPE_BILL_OF_SUPPLY);

        try {
            return invoiceRepository.save(invoice);
        } catch (Exception e) {
            writeVoid(gymId, number, fy, "pt invoice insert failed: " + e.getMessage());
            throw BusinessException.conflict("INVOICE_NUMBER_CONFLICT",
                    "Could not allocate invoice number. Please retry.");
        }
    }

    private void buildMembershipLines(Invoice invoice, Membership membership, Gym gym, LocalDate today) {
        List<Invoice.Line> lines = new ArrayList<>();
        long grossPlan = membership.getPricePaise();
        long joiningFee = Math.max(0L, membership.getJoiningFeePaise());
        long discount = Math.max(0L, membership.getDiscountPaise());

        long planDiscount = Math.min(grossPlan, discount);
        long joiningDiscount = Math.max(0L, discount - planDiscount);

        long netPlanPaise = Math.max(0L, grossPlan - planDiscount);
        long netJoiningPaise = Math.max(0L, joiningFee - joiningDiscount);

        String planDesc = "Membership - " + membership.getPlanName();
        if (planDiscount > 0) {
            planDesc += " (Less Discount)";
        }
        lines.add(createLine(planDesc, netPlanPaise, gstConfig.getSac(), gym.isGstRegistered(), today,
                invoice.getSupplierState(), invoice.getPlaceOfSupplyState()));

        if (netJoiningPaise > 0) {
            lines.add(createLine("Admission / Joining Fee", netJoiningPaise, gstConfig.getSac(), gym.isGstRegistered(), today,
                    invoice.getSupplierState(), invoice.getPlaceOfSupplyState()));
        }

        long total = netPlanPaise + netJoiningPaise;
        invoice.setLines(lines);
        invoice.setTotalPaise(total);
        invoice.setAmountInWords(IndianNumberWords.amountInWords(total));
        invoice.setType(gym.isGstRegistered() ? Invoice.TYPE_TAX_INVOICE : Invoice.TYPE_BILL_OF_SUPPLY);
    }

    private Invoice.Line createLine(String description, long totalPaise, String sac,
                                    boolean gstRegistered, LocalDate today,
                                    String supplierState, String placeOfSupplyState) {
        Invoice.Line line = new Invoice.Line();
        line.setDescription(description);
        line.setQuantity(1);
        line.setRatePaise(totalPaise);
        line.setTotalPaise(totalPaise);

        if (gstRegistered) {
            int rate = gstConfig.rateFor(today);
            line.setSac(sac != null && !sac.isBlank() ? sac : gstConfig.getSac());
            line.setGstRate(rate);
            boolean interState = placeOfSupplyState != null
                    && !placeOfSupplyState.equalsIgnoreCase(supplierState);
            GstCalculator.Breakdown b = interState
                    ? GstCalculator.splitInterstate(totalPaise, rate)
                    : GstCalculator.split(totalPaise, rate);
            line.setTaxablePaise(b.taxablePaise());
            line.setCgstPaise(b.cgstPaise());
            line.setSgstPaise(b.sgstPaise());
            line.setIgstPaise(b.igstPaise());
        } else {
            line.setGstRate(0);
            line.setTaxablePaise(totalPaise);
            line.setCgstPaise(0);
            line.setSgstPaise(0);
            line.setIgstPaise(0);
        }
        return line;
    }

    private void writeVoid(String gymId, String number, String fy, String reason) {
        try {
            Invoice v = new Invoice();
            v.setGymId(gymId);
            v.setInvoiceNumber(number);
            v.setFinancialYear(fy);
            v.setType(Invoice.TYPE_VOID);
            v.setStatus(Invoice.STATUS_VOID);
            v.setVoidReason(reason);
            v.setTotalPaise(0);
            v.setCreatedAt(clock.now());
            v.setCreatedBy(SecurityUtils.currentUserId());
            invoiceRepository.save(v);
        } catch (Exception ex) {
            auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "VOID_WRITE_FAILED",
                    "invoice", number, reason);
        }
    }

    /** Atomic gapless counter: findOneAndUpdate with $inc and upsert. */
    public long nextNumber(String gymId, String fy, String series) {
        String id = gymId + "|" + fy + "|" + series;
        Query q = Query.query(Criteria.where("_id").is(id));
        Update u = new Update().inc("seq", 1);
        FindAndModifyOptions opts = new FindAndModifyOptions()
                .upsert(true)
                .returnNew(true);
        Counter c = template.findAndModify(q, u, opts, Counter.class);
        return c == null ? 1 : c.getSeq();
    }

    public Invoice voidInvoice(String id, String reason) {
        Invoice inv = get(id);
        if (Invoice.STATUS_VOID.equals(inv.getStatus())) {
            throw BusinessException.badRequest("ALREADY_VOID", "Invoice is already void");
        }
        inv.setStatus(Invoice.STATUS_VOID);
        inv.setVoidReason(reason);
        Invoice saved = invoiceRepository.save(inv);
        auditService.log(inv.getGymId(), SecurityUtils.currentUserId(), "GYM_OWNER", "INVOICE_VOIDED",
                "invoice", inv.getInvoiceNumber(), reason);
        return saved;
    }

    /**
     * Correction for an issued invoice: a credit note in its own gapless CN series.
     * Also creates a reversing commission entry if the invoice had accrued trainer commission.
     */
    public Invoice createCreditNote(String invoiceId, String reason) {
        String gymId = SecurityUtils.currentGymId();
        Invoice original = get(invoiceId);
        if (Invoice.STATUS_VOID.equals(original.getStatus())) {
            throw BusinessException.badRequest("ALREADY_VOID", "Cannot credit a void invoice");
        }
        String fy = original.getFinancialYear();
        long seq = nextNumber(gymId, fy, SERIES_CN);
        String number = InvoiceNumbering.format("CN", fy, seq);

        Invoice cn = new Invoice();
        cn.setGymId(gymId);
        cn.setInvoiceNumber(number);
        cn.setFinancialYear(fy);
        cn.setType(Invoice.TYPE_CREDIT_NOTE);
        cn.setOriginalInvoiceId(original.getId());
        cn.setMemberId(original.getMemberId());
        cn.setMembershipId(original.getMembershipId());
        cn.setPtEnrolmentId(original.getPtEnrolmentId());
        cn.setTrainerId(original.getTrainerId());
        cn.setSupplierState(original.getSupplierState());
        cn.setPlaceOfSupplyState(original.getPlaceOfSupplyState());
        cn.setGstRegistered(original.isGstRegistered());
        cn.setGstin(original.getGstin());
        cn.setStatus(Invoice.STATUS_ISSUED);
        cn.setVoidReason(reason);
        cn.setCreatedAt(clock.now());
        cn.setCreatedBy(SecurityUtils.currentUserId());

        List<Invoice.Line> lines = new ArrayList<>();
        for (Invoice.Line l : original.getLines()) {
            Invoice.Line cl = new Invoice.Line();
            cl.setDescription("Credit note for " + original.getInvoiceNumber() + " - " + l.getDescription());
            cl.setSac(l.getSac());
            cl.setQuantity(l.getQuantity());
            cl.setRatePaise(-l.getRatePaise());
            cl.setTaxablePaise(-l.getTaxablePaise());
            cl.setGstRate(l.getGstRate());
            cl.setCgstPaise(-l.getCgstPaise());
            cl.setSgstPaise(-l.getSgstPaise());
            cl.setIgstPaise(-l.getIgstPaise());
            cl.setTotalPaise(-l.getTotalPaise());
            lines.add(cl);
        }
        cn.setLines(lines);
        cn.setTotalPaise(-original.getTotalPaise());
        cn.setAmountInWords(IndianNumberWords.amountInWords(Math.abs(original.getTotalPaise())));
        try {
            Invoice savedCn = invoiceRepository.save(cn);
            commissionService.reverseOnCreditNote(original, savedCn);
            return savedCn;
        } catch (Exception e) {
            writeVoid(gymId, number, fy, "credit note insert failed: " + e.getMessage());
            throw BusinessException.conflict("INVOICE_NUMBER_CONFLICT", "Could not allocate credit note number. Please retry.");
        }
    }

    public List<Invoice> list() {
        return invoiceRepository.findByGymIdOrderByCreatedAtDesc(SecurityUtils.currentGymId());
    }

    /**
     * Dues / Outstanding list: all non-void invoices with balanceDue > 0, including
     * partially paid and unpaid invoices, enriched with member name and phone.
     */
    public List<Map<String, Object>> listDues() {
        String gymId = SecurityUtils.currentGymId();
        List<Invoice> invoices = invoiceRepository.findByGymIdOrderByCreatedAtDesc(gymId);
        List<Map<String, Object>> dues = new ArrayList<>();
        for (Invoice inv : invoices) {
            if (Invoice.STATUS_VOID.equals(inv.getStatus())
                    || Invoice.TYPE_CREDIT_NOTE.equals(inv.getType())
                    || inv.getTotalPaise() <= 0) {
                continue;
            }
            long balance = paymentService.balanceDue(gymId, inv.getId());
            if (balance <= 0) {
                continue;
            }
            long paid = paymentService.amountPaid(gymId, inv.getId());
            Member m = inv.getMemberId() == null ? null
                    : memberRepository.findById(inv.getMemberId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("invoiceId", inv.getId());
            row.put("invoiceNumber", inv.getInvoiceNumber());
            row.put("memberId", inv.getMemberId());
            row.put("memberName", m != null ? m.getName() : "");
            row.put("memberPhone", m != null ? m.getPhone() : "");
            row.put("totalPaise", inv.getTotalPaise());
            row.put("paidPaise", paid);
            row.put("balancePaise", balance);
            row.put("status", paid > 0 ? "PARTIAL" : "UNPAID");
            row.put("payToken", inv.getPayToken());
            row.put("createdAt", inv.getCreatedAt());
            dues.add(row);
        }
        return dues;
    }

    public List<Invoice> listByMember(String memberId) {
        String gymId = SecurityUtils.currentGymId();
        return invoiceRepository.findByGymIdAndMemberIdOrderByCreatedAtDesc(gymId, memberId);
    }

    public Invoice get(String id) {
        String gymId = SecurityUtils.currentGymId();
        return invoiceRepository.findById(id)
                .filter(i -> gymId.equals(i.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("INVOICE_NOT_FOUND", "Invoice not found"));
    }

    /** Public pay page lookup by token (no auth). */
    public Invoice getByPayToken(String payToken) {
        return invoiceRepository.findByPayToken(payToken)
                .orElseThrow(() -> BusinessException.notFound("PAY_LINK_INVALID", "Invalid or expired pay link"));
    }

    /** Renders the invoice PDF for the owner. */
    public byte[] renderPdf(String invoiceId) {
        Invoice inv = get(invoiceId);
        Gym gym = gymRepository.findById(inv.getGymId()).orElse(null);
        Member member = inv.getMemberId() == null ? null
                : memberRepository.findById(inv.getMemberId()).orElse(null);
        return pdfRenderer.renderInvoice(inv, gym, member == null ? null : member.getName());
    }

    /**
     * The owner's counter pay link + QR for an invoice: a standard UPI deep link
     * to the gym's own VPA with the invoice number in the note.
     */
    public Map<String, Object> payLink(String invoiceId) {
        Invoice inv = get(invoiceId);
        Gym gym = gymRepository.findById(inv.getGymId())
                .orElseThrow(() -> BusinessException.notFound("GYM_NOT_FOUND", "Gym not found"));
        String vpa = gym.getSettings() == null ? null : gym.getSettings().getUpiVpa();
        String name = gym.getSettings() == null ? null : gym.getSettings().getUpiName();
        long balance = paymentService.balanceDue(gym.getId(), invoiceId);
        String link = vpa == null || vpa.isBlank() ? null
                : paymentProvider.buildPaymentLink(vpa, name, balance, inv.getInvoiceNumber());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("invoiceNumber", inv.getInvoiceNumber());
        out.put("amountPaise", balance);
        out.put("upiVpa", vpa == null ? "" : vpa);
        out.put("upiDeepLink", link == null ? "" : link);
        out.put("publicPayUrl", inv.getPayToken() == null ? "" : "/pay/" + inv.getPayToken());
        out.put("provider", paymentProvider.name());
        return out;
    }
}
