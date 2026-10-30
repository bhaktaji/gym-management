package com.gymkhata.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymkhata.common.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window IP rate limiter for sensitive public endpoints:
 * - /api/v1/auth/login
 * - /api/v1/auth/refresh
 * - /api/v1/auth/signup
 * - /api/v1/auth/password-reset/**
 * - /api/v1/pay/**
 * Respects X-Forwarded-For (first hop) when running behind Cloud Run / reverse proxy.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private volatile boolean enabled;
    private volatile int maxRequestsPerMinute;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    private static class WindowCounter {
        volatile long windowStartMs;
        final AtomicInteger count = new AtomicInteger(0);

        WindowCounter(long nowMs) {
            this.windowStartMs = nowMs;
        }
    }

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.security.rate-limit.requests-per-minute:30}") int maxRequestsPerMinute) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public void reset() {
        counters.clear();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        String bucket = resolveBucket(path, request.getMethod());
        if (bucket == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String key = bucket + "|" + clientIp;
        long now = System.currentTimeMillis();

        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter(now));
        synchronized (counter) {
            if (now - counter.windowStartMs >= 60_000L) {
                counter.windowStartMs = now;
                counter.count.set(0);
            }
            int current = counter.count.incrementAndGet();
            if (current > maxRequestsPerMinute) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", "60");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(
                        ApiError.of(429, "RATE_LIMITED", "Too many requests from this IP. Please retry in 60 seconds.")));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveBucket(String path, String method) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/signup")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/password-reset")) {
            return "AUTH";
        }
        if (path.startsWith("/api/v1/pay/") && "POST".equalsIgnoreCase(method)) {
            return "PAY_UTR";
        }
        return null;
    }

    private static final java.util.regex.Pattern TRUSTED_PROXY_PATTERN = java.util.regex.Pattern.compile(
            "^(127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|0:0:0:0:0:0:0:1|::1|localhost|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|169\\.254\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3})$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    public static boolean isTrustedProxy(String ip) {
        return ip != null && TRUSTED_PROXY_PATTERN.matcher(ip.trim()).matches();
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr().trim();
        // Only honour forwarded headers when the immediate peer is a trusted proxy (Nginx / Cloud Run / loopback)
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            // Walk right-to-left and return the first non-trusted-proxy IP appended by our proxy
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (!hop.isEmpty() && !isTrustedProxy(hop)) {
                    return hop;
                }
            }
            return hops[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return remoteAddr;
    }
}
