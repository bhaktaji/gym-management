package com.gymkhata.auth;

import com.gymkhata.auth.model.User;
import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.domain.PhoneUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;

    public AuthController(AuthService authService, SignupService signupService) {
        this.authService = authService;
        this.signupService = signupService;
    }

    public record LoginRequest(@NotBlank String phone, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ResetRequest(@NotBlank String phone) {
    }

    public record ResetConfirmRequest(@NotBlank String token, @NotBlank @Size(min = 8) String newPassword) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {
    }

    public record AuthResponse(String accessToken, String refreshToken, Map<String, Object> user) {
    }

    @PostMapping("/login")
    @Operation(summary = "Login with phone + password")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        AuthService.TokenPair pair = authService.login(req.phone(), req.password());
        return toResponse(pair);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return toResponse(authService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout (revokes refresh tokens)")
    public Map<String, String> logout() {
        authService.logout(SecurityUtils.currentUserId());
        return Map.of("status", "logged_out");
    }

    public record SignupRequest(@NotBlank String gymName, @NotBlank String ownerName,
                                @NotBlank String phone, String email,
                                String city, String state, @NotBlank @Size(min = 8) String password) {
    }

    @PostMapping("/signup")
    @Operation(summary = "Self-signup: create a gym (PENDING) and its single GYM_OWNER")
    public Map<String, Object> signup(@Valid @RequestBody SignupRequest req) {
        return signupService.signup(new SignupService.SignupRequest(
                req.gymName(), req.ownerName(), req.phone(), req.email(), req.city(), req.state(), req.password()));
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request a password reset (never returns plaintext reset token in API response)")
    public Map<String, String> requestReset(@Valid @RequestBody ResetRequest req) {
        authService.createPasswordResetToken(req.phone());
        return Map.of(
                "status", "reset_requested",
                "message", "If an account exists for this number, a password reset request has been logged. Please contact your platform administrator to complete verification."
        );
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Set a new password with an out-of-band reset token")
    public Map<String, String> confirmReset(@Valid @RequestBody ResetConfirmRequest req) {
        authService.resetPassword(req.token(), req.newPassword());
        return Map.of("status", "password_updated");
    }

    @PostMapping("/change-password")
    @Operation(summary = "Authenticated user changes their own password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(SecurityUtils.currentUserId(), req.currentPassword(), req.newPassword());
        return Map.of("status", "password_updated");
    }

    private AuthResponse toResponse(AuthService.TokenPair pair) {
        User u = pair.user();
        Map<String, Object> user = Map.of(
                "id", u.getId(),
                "name", u.getName() == null ? "" : u.getName(),
                "role", u.getRole(),
                "gymId", u.getGymId() == null ? "" : u.getGymId(),
                "phone", PhoneUtils.mask(u.getPhone()));
        return new AuthResponse(pair.accessToken(), pair.refreshToken(), user);
    }
}
