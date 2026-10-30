package com.gymkhata.billing;

import com.gymkhata.billing.model.Invoice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Billing")
public class BillingController {

    private final BillingService billingService;
    private final PaymentService paymentService;

    public BillingController(BillingService billingService, PaymentService paymentService) {
        this.billingService = billingService;
        this.paymentService = paymentService;
    }

    public record GenerateRequest(@NotBlank String membershipId) {
    }

    public record VoidRequest(@NotBlank String reason) {
    }

    public record CreditNoteRequest(@NotBlank String reason) {
    }

    @PostMapping("/invoices")
    @Operation(summary = "Generate the invoice for a membership (gapless numbering)")
    public Invoice generate(@Valid @RequestBody GenerateRequest req) {
        return billingService.generateInvoice(req.membershipId());
    }

    @GetMapping("/invoices")
    @Operation(summary = "List invoices")
    public List<Invoice> list() {
        return billingService.list();
    }

    @GetMapping("/dues")
    @Operation(summary = "List outstanding/partially-paid invoices with balance due")
    public List<Map<String, Object>> dues() {
        return billingService.listDues();
    }

    @GetMapping("/invoices/member/{memberId}")
    @Operation(summary = "List a member's invoices")
    public List<Invoice> byMember(@PathVariable String memberId) {
        return billingService.listByMember(memberId);
    }

    @GetMapping("/invoices/{id}")
    @Operation(summary = "Get an invoice")
    public Invoice get(@PathVariable String id) {
        return billingService.get(id);
    }

    @PostMapping("/invoices/{id}/void")
    @Operation(summary = "Void an issued invoice")
    public Invoice voidInvoice(@PathVariable String id, @Valid @RequestBody VoidRequest req) {
        return billingService.voidInvoice(id, req.reason());
    }

    @PostMapping("/invoices/{id}/credit-note")
    @Operation(summary = "Issue a credit note against an invoice")
    public Invoice creditNote(@PathVariable String id, @Valid @RequestBody CreditNoteRequest req) {
        return billingService.createCreditNote(id, req.reason());
    }

    @GetMapping("/invoices/{id}/payments")
    @Operation(summary = "List payments against an invoice")
    public List<com.gymkhata.billing.model.Payment> payments(@PathVariable String id) {
        return paymentService.listByInvoice(id);
    }

    @GetMapping("/invoices/{id}/balance")
    @Operation(summary = "Balance due on an invoice")
    public Map<String, Object> balance(@PathVariable String id) {
        String gymId = billingService.get(id).getGymId();
        return Map.of("balancePaise", paymentService.balanceDue(gymId, id));
    }

    @GetMapping(value = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the invoice as PDF")
    public byte[] pdf(@PathVariable String id) {
        return billingService.renderPdf(id);
    }

    @GetMapping("/invoices/{id}/pay-link")
    @Operation(summary = "UPI deep link + QR data for the counter")
    public Map<String, Object> payLink(@PathVariable String id) {
        return billingService.payLink(id);
    }
}
