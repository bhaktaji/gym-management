package com.gymkhata.member;

import com.gymkhata.member.model.Member;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    public record MemberRequest(@NotBlank String name, @NotBlank String phone, String email, String gender,
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dob,
                               String address, String state, boolean whatsappOptIn, String notes) {
    }

    public record MemberUpdateRequest(String name, String phone, String email, String gender,
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dob,
                                     String address, String state, Boolean whatsappOptIn, String notes) {
    }

    @GetMapping
    @Operation(summary = "List members (optional ?status=ACTIVE|INACTIVE|EXPIRED)")
    public List<Member> list(@RequestParam(required = false) String status) {
        return memberService.list(status);
    }

    @PostMapping
    @Operation(summary = "Enrol a new member (generates 4-digit PIN returned once)")
    public Member create(@Valid @RequestBody MemberRequest req) {
        return memberService.create(req.name(), req.phone(), req.email(), req.gender(), req.dob(),
                req.address(), req.state(), req.whatsappOptIn(), req.notes());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a member")
    public Member update(@PathVariable String id, @RequestBody MemberUpdateRequest req) {
        return memberService.update(id, req.name(), req.phone(), req.email(), req.gender(), req.dob(),
                req.address(), req.state(), req.whatsappOptIn(), req.notes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a member")
    public Member get(@PathVariable String id) {
        return memberService.get(id);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Activate/deactivate a member")
    public Member setStatus(@PathVariable String id, @RequestBody Map<String, String> req) {
        return memberService.setStatus(id, req.get("status"));
    }

    @PostMapping("/{id}/reset-pin")
    @Operation(summary = "Reset a member's 4-digit self check-in PIN (shown once)")
    public Map<String, Object> resetPin(@PathVariable String id) {
        return memberService.resetPin(id);
    }

    @PostMapping("/{id}/reset-pin-lockout")
    @Operation(summary = "Owner unlocks a member locked out by failed PIN attempts instantly")
    public Member resetPinLockout(@PathVariable String id) {
        return memberService.resetPinLockout(id);
    }

    @DeleteMapping("/{id}/devices")
    @Operation(summary = "Revoke all bound check-in devices for a member")
    public Member revokeAllDevices(@PathVariable String id) {
        return memberService.revokeDevice(id, null);
    }

    @DeleteMapping("/{id}/devices/{deviceId}")
    @Operation(summary = "Revoke a specific bound check-in device for a member")
    public Member revokeDevice(@PathVariable String id, @PathVariable String deviceId) {
        return memberService.revokeDevice(id, deviceId);
    }

    @PostMapping("/{id}/pending-devices/{requestId}/approve")
    @Operation(summary = "Approve a pending check-in device binding request")
    public Member approvePendingDevice(@PathVariable String id, @PathVariable String requestId) {
        return memberService.approvePendingDevice(id, requestId);
    }

    @DeleteMapping("/{id}/pending-devices/{requestId}")
    @Operation(summary = "Reject a pending check-in device binding request")
    public Member rejectPendingDevice(@PathVariable String id, @PathVariable String requestId) {
        return memberService.rejectPendingDevice(id, requestId);
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "DPDP: export all member data as JSON")
    public Map<String, Object> exportData(@PathVariable String id) {
        return memberService.exportData(id);
    }

    @PostMapping("/{id}/erase")
    @Operation(summary = "DPDP: anonymise member (invoices/payments retained)")
    public Member erase(@PathVariable String id) {
        return memberService.erase(id);
    }
}
