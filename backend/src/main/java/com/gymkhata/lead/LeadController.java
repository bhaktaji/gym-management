package com.gymkhata.lead;

import com.gymkhata.lead.model.Lead;
import com.gymkhata.member.model.Member;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    public record LeadRequest(@NotBlank String name, @NotBlank String phone, String source, String notes) {
    }

    public record LeadUpdateRequest(String status, String notes) {
    }

    @GetMapping
    @Operation(summary = "List leads (optional ?status=)")
    public List<Lead> list(@RequestParam(required = false) String status) {
        return leadService.list(status);
    }

    @PostMapping
    @Operation(summary = "Add a lead")
    public Lead create(@Valid @RequestBody LeadRequest req) {
        return leadService.create(req.name(), req.phone(), req.source(), req.notes());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update lead status/notes")
    public Lead update(@PathVariable String id, @RequestBody LeadUpdateRequest req) {
        return leadService.update(id, req.status(), req.notes());
    }

    @PostMapping("/{id}/convert")
    @Operation(summary = "Convert a lead to a member")
    public Member convert(@PathVariable String id) {
        return leadService.convert(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a lead")
    public Lead get(@PathVariable String id) {
        return leadService.get(id);
    }
}
