package com.gymkhata.checkin;

import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.messaging.model.OutboxMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkins")
@Tag(name = "Check-ins & Attendance")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    public record CheckinRequest(@NotBlank String memberId) {
    }

    @PostMapping
    @Operation(summary = "Owner manual check-in fallback (requires attendanceEnabled=true)")
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

    @GetMapping("/analytics")
    @Operation(summary = "Attendance analytics: today's visits, peak-hours chart, and not-seen-in-N-days list")
    public Map<String, Object> analytics(@RequestParam(required = false) Integer notSeenDays) {
        return checkinService.analytics(notSeenDays);
    }

    @PostMapping("/nudge/{memberId}")
    @Operation(summary = "Queue a bilingual WhatsApp nudge in the outbox for an unseen member")
    public OutboxMessage nudge(@PathVariable String memberId) {
        return checkinService.nudgeMember(memberId);
    }

    @GetMapping(value = "/poster.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download printable A4 Self Check-In QR poster PDF")
    public ResponseEntity<byte[]> posterPdf() {
        byte[] pdf = checkinService.renderPosterPdf();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"self-checkin-poster.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
