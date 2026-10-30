package com.gymkhata.checkin;

import com.gymkhata.checkin.model.Checkin;
import com.gymkhata.pt.model.PtEnrolment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * Public self-service QR check-in endpoints backing /g/{gymSlug}/in and /g/{gymSlug}/screen.
 */
@RestController
@RequestMapping("/api/v1/public/attendance/{gymSlug}")
@Tag(name = "Public Self-Service Attendance")
public class PublicAttendanceController {

    public static final String DEVICE_COOKIE_NAME = "gk_device_token";

    private final CheckinService checkinService;

    public PublicAttendanceController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    public record SelfCheckinRequest(String phone, String pin, String deviceToken,
                                     String token, Double latitude, Double longitude) {
    }

    @GetMapping("/info")
    @Operation(summary = "Public self check-in page info and bound-device check")
    public Map<String, Object> info(@PathVariable String gymSlug,
                                    @RequestHeader(value = "X-Device-Token", required = false) String headerToken,
                                    HttpServletRequest request) {
        String token = resolveToken(request, headerToken, null);
        return checkinService.getPublicAttendanceInfo(gymSlug, token);
    }

    @GetMapping("/screen")
    @Operation(summary = "Rotating 60-second QR token for /g/{slug}/screen")
    public Map<String, Object> screen(@PathVariable String gymSlug) {
        return checkinService.getRotatingScreenToken(gymSlug);
    }

    @PostMapping("/checkin")
    @Operation(summary = "Self check-in via bound device cookie or first-time phone + 4-digit PIN")
    public Map<String, Object> checkIn(@PathVariable String gymSlug,
                                       @RequestBody(required = false) SelfCheckinRequest req,
                                       @RequestHeader(value = "X-Device-Token", required = false) String headerToken,
                                       @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        String bodyToken = req == null ? null : req.deviceToken();
        String existingToken = resolveToken(request, headerToken, bodyToken);
        String clientIp = resolveClientIp(request);
        CheckinService.SelfCheckinOutcome outcome = checkinService.selfCheckIn(
                gymSlug,
                req == null ? null : req.phone(),
                req == null ? null : req.pin(),
                existingToken,
                req == null ? null : req.token(),
                req == null ? null : req.latitude(),
                req == null ? null : req.longitude(),
                userAgent,
                clientIp);

        if (outcome.newDeviceCookieToken() != null) {
            ResponseCookie cookie = ResponseCookie.from(DEVICE_COOKIE_NAME, outcome.newDeviceCookieToken())
                    .httpOnly(true)
                    .path("/")
                    .maxAge(Duration.ofDays(365))
                    .sameSite("Lax")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return outcome.body();
    }

    @PostMapping("/checkout")
    @Operation(summary = "Optional self check-out ('I'm leaving') when enabled by the gym")
    public Checkin checkOut(@PathVariable String gymSlug,
                            @RequestHeader(value = "X-Device-Token", required = false) String headerToken,
                            HttpServletRequest request) {
        String token = resolveToken(request, headerToken, null);
        return checkinService.selfCheckOut(gymSlug, token);
    }

    @PostMapping("/pt-session-done")
    @Operation(summary = "Mark 1 PT session done from the member self-service page")
    public PtEnrolment ptSessionDone(@PathVariable String gymSlug,
                                     @RequestHeader(value = "X-Device-Token", required = false) String headerToken,
                                     HttpServletRequest request) {
        String token = resolveToken(request, headerToken, null);
        return checkinService.selfPtSessionDone(gymSlug, token);
    }

    private String resolveToken(HttpServletRequest request, String headerToken, String bodyToken) {
        if (request != null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (DEVICE_COOKIE_NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        return bodyToken;
    }

    private String resolveClientIp(HttpServletRequest request) {
        return com.gymkhata.common.config.RateLimitFilter.extractClientIp(request);
    }
}
