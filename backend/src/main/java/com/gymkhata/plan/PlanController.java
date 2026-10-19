package com.gymkhata.plan;

import com.gymkhata.plan.model.Plan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    public record PlanRequest(@NotBlank String name, @NotNull @Min(1) int durationDays,
                             @NotNull @Min(0) long pricePaise) {
    }

    public record PlanUpdateRequest(String name, Integer durationDays, Long pricePaise, Boolean active) {
    }

    @GetMapping
    @Operation(summary = "List active plans")
    public List<Plan> list() {
        return planService.list();
    }

    @PostMapping
    @Operation(summary = "Create a plan")
    public Plan create(@Valid @RequestBody PlanRequest req) {
        return planService.create(req.name(), req.durationDays(), req.pricePaise());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a plan")
    public Plan update(@PathVariable String id, @RequestBody PlanUpdateRequest req) {
        return planService.update(id, req.name(), req.durationDays(), req.pricePaise(), req.active());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a plan")
    public Plan get(@PathVariable String id) {
        return planService.get(id);
    }
}
