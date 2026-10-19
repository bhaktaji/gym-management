package com.gymkhata.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_GYM_ID = "gymId";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";

    /** The development default from application.yml - never acceptable in production. */
    static final String DEV_SECRET = "gymkhata-dev-secret-change-me-0123456789abcdef0123456789abcdef";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.access-token-ttl-minutes}") long accessTtlMinutes,
                      @Value("${spring.profiles.active:default}") String activeProfile) {
        if (activeProfile.contains("prod") && DEV_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "app.jwt.secret is still the development value. Set the JWT_SECRET environment variable.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    public String generateAccessToken(String userId, String role, String gymId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .issuer(issuer)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_GYM_ID, gymId)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }
}
