package com.gymkhata.checkin;

import com.gymkhata.checkin.model.Checkin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkins")
@Tag(name = "Check-ins")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    public record CheckinRequest(@NotBlank String memberId) {
    }

    @PostMapping
    @Operation(summary = "Check a member in")
    public Checkin checkIn(@Valid @RequestBody CheckinRequest req) {
        return checkinService.checkIn(req.memberId());
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "Check a member out")
    public Checkin checkOut(@PathVariable String id) {
        return checkinService.checkOut(id);
    }

    @GetMapping("/today")
    @Operation(summary = "Today's check-ins")
    public List<Checkin> today() {
        return checkinService.today();
    }

    @GetMapping("/member/{memberId}")
    @Operation(summary = "Check-in history for a member")
    public List<Checkin> history(@PathVariable String memberId) {
        return checkinService.history(memberId);
    }
}
