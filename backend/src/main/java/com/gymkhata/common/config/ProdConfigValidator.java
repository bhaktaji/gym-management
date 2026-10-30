package com.gymkhata.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Enforces fail-fast startup validation when the 'prod' profile is active:
 * 1. JWT_SECRET must be set, >= 32 bytes, and not the dev default.
 * 2. SUPER_ADMIN_PASSWORD must be set, >= 8 chars, and not a known default/demo password.
 * 3. CORS_ALLOWED_ORIGINS must be set and must not contain wildcard '*' or localhost.
 */
@Component
public class ProdConfigValidator {

    public static final String DEV_JWT_SECRET = "gymkhata-dev-secret-change-me-0123456789abcdef0123456789abcdef";
    public static final String DEFAULT_ADMIN_PASSWORD = "ChangeMe@123";
    public static final String DEMO_ADMIN_PASSWORD = "DemoAdmin@2026";

    private final Environment environment;
    private final String jwtSecret;
    private final String superAdminPassword;
    private final String corsAllowedOrigins;

    public ProdConfigValidator(Environment environment,
                               @Value("${app.jwt.secret:}") String jwtSecret,
                               @Value("${app.super-admin.password:}") String superAdminPassword,
                               @Value("${app.cors-allowed-origins:}") String corsAllowedOrigins) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.superAdminPassword = superAdminPassword;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @PostConstruct
    public void validateOnStartup() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        boolean isProd = profiles.stream()
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("prod") || p.equals("production") || p.startsWith("prod-"));
        if (isProd) {
            validateProdSettings(jwtSecret, superAdminPassword, corsAllowedOrigins);
        }
    }

    public static void validateProdSettings(String jwtSecret, String superAdminPassword, String corsAllowedOrigins) {
        if (jwtSecret == null || jwtSecret.isBlank()
                || DEV_JWT_SECRET.equals(jwtSecret)
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "PROD_CONFIG_ERROR: JWT_SECRET (app.jwt.secret) is missing, shorter than 32 bytes, or set to the dev default.");
        }
        if (superAdminPassword == null || superAdminPassword.isBlank()
                || superAdminPassword.length() < 8
                || DEFAULT_ADMIN_PASSWORD.equals(superAdminPassword)
                || DEMO_ADMIN_PASSWORD.equals(superAdminPassword)) {
            throw new IllegalStateException(
                    "PROD_CONFIG_ERROR: SUPER_ADMIN_PASSWORD (app.super-admin.password) is missing, weak, or set to the default value.");
        }
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()
                || corsAllowedOrigins.contains("*")
                || corsAllowedOrigins.toLowerCase(Locale.ROOT).contains("localhost")
                || corsAllowedOrigins.contains("127.0.0.1")) {
            throw new IllegalStateException(
                    "PROD_CONFIG_ERROR: CORS_ALLOWED_ORIGINS (app.cors-allowed-origins) must be explicitly set to production HTTPS origins and must not contain '*' or localhost.");
        }
    }
}
