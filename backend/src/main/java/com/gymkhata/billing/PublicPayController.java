package com.gymkhata.billing;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.billing.model.Invoice;
import com.gymkhata.billing.model.Payment;
import com.gymkhata.domain.Money;
import com.gymkhata.domain.UpiUtils;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Public tokenized pay page API. The member opens /pay/{token}, sees the amount
 * and a UPI deep link + QR, pays from any UPI app, and submits the 12-digit UTR.
 */
@RestController
@RequestMapping("/api/v1/pay")
@Tag(name = "Public Pay")
public class PublicPayController {

    private final BillingService billingService;
    private final PaymentService paymentService;
    private final GymRepository gymRepository;

    public PublicPayController(BillingService billingService, PaymentService paymentService, GymRepository gymRepository) {
        this.billingService = billingService;
        this.paymentService = paymentService;
        this.gymRepository = gymRepository;
    }

    public record UtrSubmitRequest(@NotNull @Positive long amountPaise, @NotBlank String utr) {
    }

    @GetMapping("/{token}")
    @Operation(summary = "Public pay page: invoice details + UPI deep link")
    public Map<String, Object> payPage(@PathVariable String token) {
        Invoice inv = billingService.getByPayToken(token);
        Gym gym = gymRepository.findById(inv.getGymId()).orElse(null);
        String vpa = gym != null && gym.getSettings() != null ? gym.getSettings().getUpiVpa() : null;
        String name = gym != null && gym.getSettings() != null ? gym.getSettings().getUpiName() : null;
        String deepLink = (vpa != null && !vpa.isBlank())
                ? UpiUtils.buildDeepLink(vpa, name, inv.getTotalPaise(), inv.getInvoiceNumber())
                : null;
        return Map.of(
                "invoiceNumber", inv.getInvoiceNumber(),
                "amountPaise", inv.getTotalPaise(),
                "amount", Money.formatIndian(inv.getTotalPaise()),
                "gymName", gym != null ? gym.getName() : "",
                "upiVpa", vpa == null ? "" : vpa,
                "upiDeepLink", deepLink == null ? "" : deepLink,
                "status", inv.getStatus());
    }

    @GetMapping(value = "/{token}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "QR code (PNG) for the UPI deep link")
    public byte[] qr(@PathVariable String token) throws Exception {
        Invoice inv = billingService.getByPayToken(token);
        Gym gym = gymRepository.findById(inv.getGymId()).orElse(null);
        String vpa = gym != null && gym.getSettings() != null ? gym.getSettings().getUpiVpa() : null;
        String name = gym != null && gym.getSettings() != null ? gym.getSettings().getUpiName() : null;
        String deepLink = (vpa != null && !vpa.isBlank())
                ? UpiUtils.buildDeepLink(vpa, name, inv.getTotalPaise(), inv.getInvoiceNumber())
                : "upi://pay?pa=&am=" + inv.getTotalPaise();
        BitMatrix matrix = new QRCodeWriter().encode(deepLink, BarcodeFormat.QR_CODE, 300, 300);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return out.toByteArray();
    }

    @PostMapping("/{token}/utr")
    @Operation(summary = "Member submits a 12-digit UTR (stays pending until owner confirms)")
    public Payment submitUtr(@PathVariable String token, @Valid @RequestBody UtrSubmitRequest req) {
        Invoice inv = billingService.getByPayToken(token);
        return paymentService.submitUpi(inv.getId(), req.amountPaise(), req.utr());
    }
}
