package com.gymkhata.billing;

import com.gymkhata.billing.model.Payment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record CounterPaymentRequest(@NotBlank String invoiceId, @NotNull @Positive long amountPaise,
                                       @NotBlank String method) {
    }

    public record ConfirmRequest(@NotBlank String paymentId) {
    }

    @PostMapping("/counter")
    @Operation(summary = "Record a cash/card payment at the counter (confirmed immediately)")
    public Payment recordCounter(@Valid @RequestBody CounterPaymentRequest req) {
        return paymentService.recordCounterPayment(req.invoiceId(), req.amountPaise(), req.method());
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a member-submitted UPI payment")
    public Payment confirm(@PathVariable String id) {
        return paymentService.confirmUpi(id);
    }

    @GetMapping("/pending")
    @Operation(summary = "List payments pending verification")
    public List<Payment> pending() {
        return paymentService.listPending();
    }
}
