package com.gymkhata.membership;

import com.gymkhata.membership.model.Membership;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/memberships")
@Tag(name = "Memberships")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    public record EnrolRequest(@NotBlank String memberId, @NotBlank String planId,
                               Long joiningFeePaise, Long discountPaise, String referralTrainerId) {
    }

    public record FreezeRequest(Integer days, String reason) {
    }

    public record UnfreezeRequest(Integer days) {
    }

    @PostMapping
    @Operation(summary = "Enrol a member into a plan (with optional joining fee, discount, referral trainer)")
    public Membership enrol(@Valid @RequestBody EnrolRequest req) {
        return membershipService.enrol(req.memberId(), req.planId(),
                req.joiningFeePaise(), req.discountPaise(), req.referralTrainerId());
    }

    @PostMapping("/{id}/freeze")
    @Operation(summary = "Freeze/pause a membership and extend expiry")
    public Membership freeze(@PathVariable String id, @RequestBody(required = false) FreezeRequest req) {
        return membershipService.freeze(id, req == null ? null : req.days(), req == null ? null : req.reason());
    }

    @PostMapping("/{id}/unfreeze")
    @Operation(summary = "Unfreeze a frozen membership and extend expiry by frozen days")
    public Membership unfreeze(@PathVariable String id, @RequestBody(required = false) UnfreezeRequest req) {
        return membershipService.unfreeze(id, req == null ? null : req.days());
    }

    @GetMapping("/member/{memberId}")
    @Operation(summary = "List a member's memberships")
    public List<Membership> byMember(@PathVariable String memberId) {
        return membershipService.listByMember(memberId);
    }

    @GetMapping("/active")
    @Operation(summary = "List active memberships")
    public List<Membership> active() {
        return membershipService.listActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a membership")
    public Membership get(@PathVariable String id) {
        return membershipService.get(id);
    }
}
