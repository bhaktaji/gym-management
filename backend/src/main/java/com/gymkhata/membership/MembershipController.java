package com.gymkhata.membership;

import com.gymkhata.membership.model.Membership;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/memberships")
@Tag(name = "Memberships")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    public record EnrolRequest(@NotBlank String memberId, @NotBlank String planId) {
    }

    @PostMapping
    @Operation(summary = "Enrol a member into a plan")
    public Membership enrol(@Valid @RequestBody EnrolRequest req) {
        return membershipService.enrol(req.memberId(), req.planId());
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
