package com.gymkhata.billing;

import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.billing.repo.InvoiceRepository;
import com.gymkhata.billing.repo.PaymentRepository;
import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.UpiUtils;
import com.gymkhata.pt.CommissionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final CommissionService commissionService;
    private final Clock clock;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          CommissionService commissionService,
                          Clock clock,
                          AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.commissionService = commissionService;
        this.clock = clock;
        this.auditService = auditService;
    }

    public long amountPaid(String gymId, String invoiceId) {
        return paymentRepository.findByGymIdAndInvoiceId(gymId, invoiceId).stream()
                .filter(p -> Payment.STATUS_CONFIRMED.equals(p.getStatus()))
                .mapToLong(Payment::getAmountPaise)
                .sum();
    }

    public long balanceDue(String gymId, String invoiceId) {
        Invoice inv = invoiceRepository.findById(invoiceId)
                .filter(i -> gymId.equals(i.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("INVOICE_NOT_FOUND", "Invoice not found"));
        if (Invoice.STATUS_VOID.equals(inv.getStatus()) || Invoice.TYPE_CREDIT_NOTE.equals(inv.getType())) {
            return 0L;
        }
        long creditedPaise = invoiceRepository.findByGymIdAndOriginalInvoiceId(gymId, invoiceId).stream()
                .filter(cn -> Invoice.TYPE_CREDIT_NOTE.equals(cn.getType()) && !Invoice.STATUS_VOID.equals(cn.getStatus()))
                .mapToLong(cn -> Math.abs(cn.getTotalPaise()))
                .sum();
        return Math.max(0L, inv.getTotalPaise() - creditedPaise - amountPaid(gymId, invoiceId));
    }

    /**
     * Owner records cash/card at the counter: confirmed immediately.
     * Partial payments allowed; overpayment rejected.
     * Automatically triggers proportional commission accrual if applicable.
     */
    public Payment recordCounterPayment(String invoiceId, long amountPaise, String method) {
        if (!Payment.METHOD_CASH.equals(method) && !Payment.METHOD_CARD.equals(method)) {
            throw BusinessException.badRequest("INVALID_METHOD", "Method must be CASH or CARD");
        }
        String gymId = SecurityUtils.currentGymId();
        Invoice inv = invoiceRepository.findById(invoiceId)
                .filter(i -> gymId.equals(i.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("INVOICE_NOT_FOUND", "Invoice not found"));
        if (Invoice.STATUS_VOID.equals(inv.getStatus())) {
            throw BusinessException.badRequest("INVOICE_VOID", "Cannot pay a void invoice");
        }
        if (amountPaise <= 0) {
            throw BusinessException.badRequest("INVALID_AMOUNT", "Amount must be > 0");
        }
        long previousPaid = amountPaid(gymId, invoiceId);
        long balance = balanceDue(gymId, invoiceId);
        if (amountPaise > balance) {
            throw BusinessException.badRequest("OVERPAYMENT",
                    "Amount exceeds balance due of " + balance);
        }
        Payment p = new Payment();
        p.setGymId(gymId);
        p.setInvoiceId(invoiceId);
        p.setMemberId(inv.getMemberId());
        p.setAmountPaise(amountPaise);
        p.setMethod(method);
        p.setStatus(Payment.STATUS_CONFIRMED);
        p.setRecordedBy(Payment.RECORDED_BY_OWNER);
        p.setConfirmedAt(clock.now());
        p.setCreatedAt(clock.now());
        Payment saved = paymentRepository.save(p);

        commissionService.onPaymentConfirmed(inv, saved, previousPaid);

        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "PAYMENT_RECORDED", "payment",
                saved.getId(), method + " " + amountPaise + " invoice=" + inv.getInvoiceNumber());
        return saved;
    }

    /**
     * Member submits a UTR on the public pay page: stays PENDING_VERIFICATION
     * until the Gym Owner confirms. Duplicate UTR within a gym is rejected.
     */
    public Payment submitUpi(String invoiceId, long amountPaise, String utr) {
        String trimmed = utr == null ? null : utr.trim();
        if (!UpiUtils.isValidUtr(trimmed)) {
            throw BusinessException.badRequest("INVALID_UTR", "UTR must be exactly 12 digits");
        }
        // gymId is resolved from the invoice (the pay token is the authorization on the public page)
        Invoice inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> BusinessException.notFound("INVOICE_NOT_FOUND", "Invoice not found"));
        String gymId = inv.getGymId();
        if (Invoice.STATUS_VOID.equals(inv.getStatus())) {
            throw BusinessException.badRequest("INVOICE_VOID", "Cannot pay a void invoice");
        }
        if (amountPaise <= 0) {
            throw BusinessException.badRequest("INVALID_AMOUNT", "Amount must be > 0");
        }
        long balance = balanceDue(gymId, invoiceId);
        if (amountPaise > balance) {
            throw BusinessException.badRequest("OVERPAYMENT", "Amount exceeds balance due of " + balance);
        }
        if (paymentRepository.findByGymIdAndUtr(gymId, trimmed).isPresent()) {
            throw BusinessException.conflict("DUPLICATE_UTR", "This UTR was already submitted");
        }
        Payment p = new Payment();
        p.setGymId(gymId);
        p.setInvoiceId(invoiceId);
        p.setMemberId(inv.getMemberId());
        p.setAmountPaise(amountPaise);
        p.setMethod(Payment.METHOD_UPI);
        p.setStatus(Payment.STATUS_PENDING);
        p.setUtr(trimmed);
        p.setRecordedBy(Payment.RECORDED_BY_MEMBER);
        p.setCreatedAt(clock.now());
        try {
            return paymentRepository.save(p);
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict("DUPLICATE_UTR", "This UTR was already submitted");
        }
    }

    /** Owner confirms a member-submitted UPI payment. */
    public Payment confirmUpi(String paymentId) {
        String gymId = SecurityUtils.currentGymId();
        Payment p = paymentRepository.findById(paymentId)
                .filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        if (!Payment.STATUS_PENDING.equals(p.getStatus())) {
            throw BusinessException.badRequest("NOT_PENDING", "Payment is not pending verification");
        }
        long previousPaid = amountPaid(gymId, p.getInvoiceId());
        p.setStatus(Payment.STATUS_CONFIRMED);
        p.setConfirmedAt(clock.now());
        Payment saved = paymentRepository.save(p);

        Invoice inv = invoiceRepository.findById(p.getInvoiceId()).orElse(null);
        if (inv != null) {
            commissionService.onPaymentConfirmed(inv, saved, previousPaid);
        }

        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "PAYMENT_CONFIRMED", "payment",
                saved.getId(), "utr=" + p.getUtr());
        return saved;
    }

    /** Owner rejects an invalid or unverified member-submitted UPI UTR. */
    public Payment rejectUpi(String paymentId, String reason) {
        String gymId = SecurityUtils.currentGymId();
        Payment p = paymentRepository.findById(paymentId)
                .filter(x -> gymId.equals(x.getGymId()))
                .orElseThrow(() -> BusinessException.notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        if (!Payment.STATUS_PENDING.equals(p.getStatus())) {
            throw BusinessException.badRequest("NOT_PENDING", "Payment is not pending verification");
        }
        p.setStatus(Payment.STATUS_FAILED);
        p.setConfirmedAt(clock.now());
        Payment saved = paymentRepository.save(p);
        String detail = "utr=" + p.getUtr() + (reason != null && !reason.isBlank() ? " reason=" + reason.trim() : "");
        auditService.log(gymId, SecurityUtils.currentUserId(), "GYM_OWNER", "PAYMENT_REJECTED", "payment",
                saved.getId(), detail);
        return saved;
    }

    public List<Payment> listByInvoice(String invoiceId) {
        String gymId = SecurityUtils.currentGymId();
        return paymentRepository.findByGymIdAndInvoiceId(gymId, invoiceId);
    }

    public List<Payment> listPending() {
        String gymId = SecurityUtils.currentGymId();
        return paymentRepository.findByGymIdAndStatus(gymId, Payment.STATUS_PENDING);
    }
}
