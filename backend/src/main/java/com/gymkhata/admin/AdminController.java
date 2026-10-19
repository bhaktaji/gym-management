package com.gymkhata.admin;

import com.gymkhata.common.audit.AuditService;
import com.gymkhata.common.error.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Super Admin")
public class AdminController {

    private final AdminService adminService;
    private final AuditService auditService;

    public AdminController(AdminService adminService, AuditService auditService) {
        this.adminService = adminService;
        this.auditService = auditService;
    }

    @GetMapping("/gyms")
    @Operation(summary = "List all gyms with summary stats")
    public List<AdminService.GymSummary> listGyms() {
        return adminService.listGyms();
    }

    public record CreateGymRequest(@NotBlank String gymName, @NotBlank String ownerName,
                                  @NotBlank String phone, String email,
                                  String city, String state, String gstin,
                                  boolean gstRegistered, @NotBlank String password,
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate subscriptionValidUntil) {
    }

    @PostMapping("/gyms")
    @Operation(summary = "Create a gym and its single GYM_OWNER account")
    public Map<String, Object> createGym(@Valid @RequestBody CreateGymRequest req) {
        var result = adminService.createGymWithOwner(new AdminService.CreateGymRequest(
                req.gymName(), req.ownerName(), req.phone(), req.email(), req.city(), req.state(),
                req.gstin(), req.gstRegistered(), req.password(), req.subscriptionValidUntil()));
        auditService.log(null, "system", "SUPER_ADMIN", "GYM_CREATED", "gym",
                String.valueOf(result.get("gymId")), "gymName=" + req.gymName());
        return result;
    }

    public record OwnerContactRequest(String phone, String email) {
    }

    @PutMapping("/gyms/{gymId}/owner")
    @Operation(summary = "Update the gym owner's phone/email")
    public Map<String, Object> updateOwner(@PathVariable String gymId, @RequestBody OwnerContactRequest req) {
        var result = adminService.updateOwnerContact(gymId, req.phone(), req.email());
        auditService.log(gymId, "system", "SUPER_ADMIN", "OWNER_UPDATED", "gym", gymId, "owner contact updated");
        return result;
    }

    @PostMapping("/gyms/{gymId}/password-reset")
    @Operation(summary = "Trigger a password reset for the gym owner")
    public Map<String, Object> triggerReset(@PathVariable String gymId) {
        var result = adminService.triggerPasswordReset(gymId);
        auditService.log(gymId, "system", "SUPER_ADMIN", "PASSWORD_RESET_TRIGGERED", "gym", gymId, null);
        return result;
    }

    public record StatusRequest(@NotBlank String status) {
    }

    @PostMapping("/gyms/{gymId}/status")
    @Operation(summary = "Activate or suspend a gym")
    public Map<String, Object> setStatus(@PathVariable String gymId, @Valid @RequestBody StatusRequest req) {
        var result = adminService.setGymStatus(gymId, req.status());
        auditService.log(gymId, "system", "SUPER_ADMIN", "GYM_STATUS_CHANGED", "gym", gymId, "status=" + req.status());
        return result;
    }

    public record SubscriptionRequest(@NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil) {
    }

    @PutMapping("/gyms/{gymId}/subscription")
    @Operation(summary = "Update the gym's SaaS subscription valid-until date")
    public Map<String, Object> setSubscription(@PathVariable String gymId, @Valid @RequestBody SubscriptionRequest req) {
        var result = adminService.setSubscription(gymId, req.validUntil());
        auditService.log(gymId, "system", "SUPER_ADMIN", "SUBSCRIPTION_UPDATED", "gym", gymId,
                "validUntil=" + req.validUntil());
        return result;
    }
}
