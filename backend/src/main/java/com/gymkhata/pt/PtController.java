package com.gymkhata.pt;

import com.gymkhata.domain.CommissionRule;
import com.gymkhata.pt.model.CommissionEntry;
import com.gymkhata.pt.model.PtEnrolment;
import com.gymkhata.pt.model.PtPackage;
import com.gymkhata.pt.model.Trainer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Personal Training & Commissions")
public class PtController {

    private final PtService ptService;
    private final CommissionService commissionService;

    public PtController(PtService ptService, CommissionService commissionService) {
        this.ptService = ptService;
        this.commissionService = commissionService;
    }

    // --- Trainers ---

    public record TrainerRequest(@NotBlank String name, @NotBlank String phone,
                                 Boolean active, CommissionRule defaultCommissionRule) {
    }

    public record TrainerUpdateRequest(String name, String phone,
                                       Boolean active, CommissionRule defaultCommissionRule) {
    }

    @GetMapping("/trainers")
    @Operation(summary = "List trainers for the current gym")
    public List<Trainer> listTrainers(@RequestParam(required = false) Boolean active) {
        return ptService.listTrainers(active);
    }

    @PostMapping("/trainers")
    @Operation(summary = "Create a trainer record (no login account)")
    public Trainer createTrainer(@Valid @RequestBody TrainerRequest req) {
        return ptService.createTrainer(req.name(), req.phone(), req.active(), req.defaultCommissionRule());
    }

    @PutMapping("/trainers/{id}")
    @Operation(summary = "Update trainer details or default commission rule (effective-dated)")
    public Trainer updateTrainer(@PathVariable String id, @RequestBody TrainerUpdateRequest req) {
        return ptService.updateTrainer(id, req.name(), req.phone(), req.active(), req.defaultCommissionRule());
    }

    // --- PT Packages ---

    public record PtPackageRequest(@NotBlank String name, String packageType,
                                   @NotNull @Min(1) int validityDays, Integer totalSessions,
                                   @NotNull @Min(0) long pricePaise, String sacCode,
                                   CommissionRule commissionRuleOverride) {
    }

    public record PtPackageUpdateRequest(String name, String packageType, Integer validityDays,
                                         Integer totalSessions, Long pricePaise, String sacCode,
                                         CommissionRule commissionRuleOverride, Boolean active) {
    }

    @GetMapping("/pt/packages")
    @Operation(summary = "List Personal Training packages")
    public List<PtPackage> listPackages(@RequestParam(required = false) Boolean active) {
        return ptService.listPackages(active);
    }

    @PostMapping("/pt/packages")
    @Operation(summary = "Create a Personal Training package")
    public PtPackage createPackage(@Valid @RequestBody PtPackageRequest req) {
        return ptService.createPackage(req.name(), req.packageType(), req.validityDays(),
                req.totalSessions(), req.pricePaise(), req.sacCode(), req.commissionRuleOverride());
    }

    @PutMapping("/pt/packages/{id}")
    @Operation(summary = "Update a Personal Training package")
    public PtPackage updatePackage(@PathVariable String id, @RequestBody PtPackageUpdateRequest req) {
        return ptService.updatePackage(id, req.name(), req.packageType(), req.validityDays(),
                req.totalSessions(), req.pricePaise(), req.sacCode(), req.commissionRuleOverride(), req.active());
    }

    // --- PT Enrolments & Session Tracking ---

    public record SellPtRequest(@NotBlank String memberId, @NotBlank String ptPackageId,
                                @NotBlank String trainerId, Long discountPaise) {
    }

    @PostMapping("/pt/enrolments")
    @Operation(summary = "Sell a PT package to a member with a trainer (automatically generates GST invoice)")
    public PtEnrolment sellPt(@Valid @RequestBody SellPtRequest req) {
        return ptService.sellPtPackage(req.memberId(), req.ptPackageId(), req.trainerId(), req.discountPaise());
    }

    @GetMapping("/pt/enrolments")
    @Operation(summary = "List all PT enrolments")
    public List<PtEnrolment> listEnrolments() {
        return ptService.listEnrolments();
    }

    @GetMapping("/pt/enrolments/member/{memberId}")
    @Operation(summary = "List PT enrolments for a member")
    public List<PtEnrolment> listMemberEnrolments(@PathVariable String memberId) {
        return ptService.listEnrolmentsByMember(memberId);
    }

    @PostMapping("/pt/enrolments/{id}/session-done")
    @Operation(summary = "Decrement 1 PT session from an active PT enrolment")
    public PtEnrolment markSessionDone(@PathVariable String id) {
        return ptService.markSessionDone(id);
    }

    // --- Commissions & Statements ---

    public record AdjustmentRequest(@NotBlank String trainerId, String month,
                                    @NotNull long amountPaise, @NotBlank String reason) {
    }

    public record PayoutRequest(@NotBlank String trainerId, String month,
                                @NotNull @Min(1) long amountPaise, @NotBlank String payoutMode,
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payoutDate,
                                String notes) {
    }

    @GetMapping("/commissions/statement")
    @Operation(summary = "Monthly trainer commission statement (accrued, reversals, adjustments, paid, balance due)")
    public Map<String, Object> statement(@RequestParam String trainerId,
                                         @RequestParam(required = false) String month) {
        return commissionService.getStatement(trainerId, month);
    }

    @PostMapping("/commissions/adjustments")
    @Operation(summary = "Record a manual trainer commission adjustment with reason")
    public CommissionEntry addAdjustment(@Valid @RequestBody AdjustmentRequest req) {
        return commissionService.addAdjustment(req.trainerId(), req.month(), req.amountPaise(), req.reason());
    }

    @PostMapping("/commissions/payouts")
    @Operation(summary = "Mark a trainer commission payout as paid (records date + mode)")
    public CommissionEntry markPayoutPaid(@Valid @RequestBody PayoutRequest req) {
        return commissionService.markPayoutPaid(req.trainerId(), req.month(), req.amountPaise(),
                req.payoutMode(), req.payoutDate(), req.notes());
    }

    @GetMapping(value = "/commissions/statement.csv", produces = "text/csv")
    @Operation(summary = "Export monthly trainer commission statement as CSV")
    public ResponseEntity<byte[]> exportCsv(@RequestParam String trainerId,
                                            @RequestParam(required = false) String month) {
        String csv = commissionService.exportStatementCsv(trainerId, month);
        String effMonth = (month == null || month.isBlank()) ? commissionService.currentMonth() : month;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"commission-" + trainerId + "-" + effMonth + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/commissions/statement.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Export monthly trainer commission statement as PDF")
    public ResponseEntity<byte[]> exportPdf(@RequestParam String trainerId,
                                            @RequestParam(required = false) String month) {
        byte[] pdf = commissionService.exportStatementPdf(trainerId, month);
        String effMonth = (month == null || month.isBlank()) ? commissionService.currentMonth() : month;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"commission-" + trainerId + "-" + effMonth + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
