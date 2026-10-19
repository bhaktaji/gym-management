package com.gymkhata.auth;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.model.PasswordResetToken;
import com.gymkhata.auth.model.RefreshToken;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.repo.PasswordResetTokenRepository;
import com.gymkhata.auth.repo.RefreshTokenRepository;
import com.gymkhata.auth.repo.UserRepository;
import com.gymkhata.auth.security.JwtService;
import com.gymkhata.common.clock.Clock;
import com.gymkhata.common.error.BusinessException;
import com.gymkhata.domain.PhoneUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final Duration refreshTtl;
    private final Duration resetTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       GymRepository gymRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       Clock clock,
                       @Value("${app.jwt.refresh-token-ttl-days}") long refreshTtlDays,
                       @Value("${app.security.password-reset-ttl-minutes}") long resetTtlMinutes) {
        this.userRepository = userRepository;
        this.gymRepository = gymRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.resetTtl = Duration.ofMinutes(resetTtlMinutes);
    }

    public record TokenPair(String accessToken, String refreshToken, User user) {
    }

    public TokenPair login(String phone, String password) {
        String e164 = PhoneUtils.normalize(phone);
        if (e164 == null) {
            throw BusinessException.badRequest("INVALID_PHONE", "Enter a valid 10-digit mobile number");
        }
        User user = userRepository.findByPhone(e164)
                .orElseThrow(() -> BusinessException.forbidden("INVALID_CREDENTIALS", "Invalid phone or password"));
        if (!User.STATUS_ACTIVE.equals(user.getStatus())) {
            throw BusinessException.forbidden("ACCOUNT_SUSPENDED", "This account is suspended");
        }
        if (User.ROLE_GYM_OWNER.equals(user.getRole()) && user.getGymId() != null) {
            Gym gym = gymRepository.findById(user.getGymId()).orElse(null);
            if (gym == null || !Gym.STATUS_ACTIVE.equals(gym.getStatus())) {
                throw BusinessException.forbidden("ACCOUNT_SUSPENDED", "This gym account is suspended");
            }
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BusinessException.forbidden("INVALID_CREDENTIALS", "Invalid phone or password");
        }
        user.setLastLoginAt(clock.now());
        userRepository.save(user);
        return issueTokens(user);
    }

    public TokenPair refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw BusinessException.forbidden("INVALID_REFRESH_TOKEN", "Missing refresh token");
        }
        String hash = sha256(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> BusinessException.forbidden("INVALID_REFRESH_TOKEN", "Invalid refresh token"));
        if (stored.getRevokedAt() != null) {
            // reuse of a rotated token: revoke the whole family
            refreshTokenRepository.findByFamilyId(stored.getFamilyId())
                    .forEach(t -> {
                        t.setRevokedAt(clock.now());
                        refreshTokenRepository.save(t);
                    });
            throw BusinessException.forbidden("REFRESH_REUSE_DETECTED", "Session revoked. Please log in again.");
        }
        if (stored.getExpiresAt().isBefore(clock.now())) {
            throw BusinessException.forbidden("REFRESH_EXPIRED", "Session expired. Please log in again.");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> BusinessException.forbidden("INVALID_REFRESH_TOKEN", "User not found"));
        if (!User.STATUS_ACTIVE.equals(user.getStatus())) {
            throw BusinessException.forbidden("ACCOUNT_SUSPENDED", "This account is suspended");
        }
        if (User.ROLE_GYM_OWNER.equals(user.getRole()) && user.getGymId() != null) {
            Gym gym = gymRepository.findById(user.getGymId()).orElse(null);
            if (gym == null || !Gym.STATUS_ACTIVE.equals(gym.getStatus())) {
                throw BusinessException.forbidden("ACCOUNT_SUSPENDED", "This gym account is suspended");
            }
        }
        // rotation: revoke old, issue new
        stored.setRevokedAt(clock.now());
        refreshTokenRepository.save(stored);
        TokenPair pair = issueTokens(user);
        stored.setReplacedBy(sha256(pair.refreshToken()));
        refreshTokenRepository.save(stored);
        return pair;
    }

    public TokenPair issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole(), user.getGymId());
        String raw = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(sha256(raw));
        rt.setFamilyId(UUID.randomUUID().toString());
        rt.setExpiresAt(clock.now().plus(refreshTtl));
        rt.setCreatedAt(clock.now());
        refreshTokenRepository.save(rt);
        return new TokenPair(accessToken, raw, user);
    }

    public void logout(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public String createPasswordResetToken(String phone) {
        String e164 = PhoneUtils.normalize(phone);
        User user = e164 == null ? null : userRepository.findByPhone(e164).orElse(null);
        if (user == null) {
            // do not reveal whether the phone exists
            return null;
        }
        String raw = UUID.randomUUID().toString();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(clock.now().plus(resetTtl));
        token.setCreatedAt(clock.now());
        passwordResetTokenRepository.save(token);
        return raw;
    }

    public void resetPassword(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw BusinessException.badRequest("WEAK_PASSWORD", "Password must be at least 8 characters");
        }
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> BusinessException.forbidden("INVALID_RESET_TOKEN", "Invalid or expired reset token"));
        if (token.getUsedAt() != null) {
            throw BusinessException.forbidden("RESET_TOKEN_USED", "This reset link was already used");
        }
        if (token.getExpiresAt().isBefore(clock.now())) {
            throw BusinessException.forbidden("RESET_TOKEN_EXPIRED", "This reset link has expired");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> BusinessException.forbidden("INVALID_RESET_TOKEN", "User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        token.setUsedAt(clock.now());
        passwordResetTokenRepository.save(token);
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    private String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
