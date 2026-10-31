package com.gymkhata.common.config;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@RestController
public class PublicConfigController {

    private static final String STARTUP_BUILD_TIME = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();

    @Value("${app.name}")
    private String appName;

    @Value("${app.pricing.monthly-price-paise}")
    private int monthlyPricePaise;

    @Value("${app.pricing.currency}")
    private String currency;

    @Value("${app.pricing.included}")
    private List<String> included;

    @Value("${app.pricing.not-charged}")
    private List<String> notCharged;

    @Value("${app.build.commit-hash:}")
    private String configuredCommitHash;

    @Value("${app.build.build-time:}")
    private String configuredBuildTime;

    @GetMapping("/api/v1/config/public")
    @Operation(summary = "Public platform config (name, pricing) for landing/pricing pages")
    public Map<String, Object> publicConfig() {
        return Map.of(
                "name", appName,
                "pricing", Map.of(
                        "monthlyPricePaise", monthlyPricePaise,
                        "currency", currency,
                        "included", included,
                        "notCharged", notCharged));
    }

    @GetMapping("/api/v1/build-info")
    @Operation(summary = "Public build info (git commit hash and build time only)")
    public Map<String, String> buildInfo() {
        String hash = configuredCommitHash;
        String time = configuredBuildTime;
        try (InputStream is = getClass().getResourceAsStream("/build-info.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                if (hash == null || hash.isBlank()) {
                    hash = props.getProperty("commitHash", "");
                }
                if (time == null || time.isBlank()) {
                    time = props.getProperty("buildTime", "");
                }
            }
        } catch (Exception ignored) {
        }
        if (hash == null || hash.isBlank()) {
            hash = resolveGitCommitShort();
        }
        if (time == null || time.isBlank()) {
            time = STARTUP_BUILD_TIME;
        }
        Map<String, String> out = new LinkedHashMap<>();
        out.put("commitHash", hash);
        out.put("buildTime", time);
        return out;
    }

    private static String resolveGitCommitShort() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            String s = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() == 0 && !s.isBlank() && s.length() <= 40 && !s.contains(" ")) {
                return s;
            }
        } catch (Exception ignored) {
        }
        return "release";
    }
}
