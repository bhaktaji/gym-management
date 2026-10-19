package com.gymkhata.tenant;

import com.gymkhata.admin.model.Gym;
import com.gymkhata.admin.repo.GymRepository;
import com.gymkhata.auth.model.User;
import com.gymkhata.auth.security.SecurityUtils;
import com.gymkhata.common.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks suspended gyms from tenant APIs with 403 ACCOUNT_SUSPENDED.
 * Gym status is cached briefly to avoid a DB round-trip per request.
 */
@Component
public class TenantStatusInterceptor implements HandlerInterceptor {

    private final GymRepository gymRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CachedStatus> cache = new ConcurrentHashMap<>();

    public TenantStatusInterceptor(GymRepository gymRepository) {
        this.gymRepository = gymRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        User user = SecurityUtils.currentUser();
        if (!User.ROLE_GYM_OWNER.equals(user.getRole())) {
            return true;
        }
        String gymId = user.getGymId();
        CachedStatus cached = cache.get(gymId);
        long now = System.currentTimeMillis();
        if (cached == null || cached.expiresAt() < now) {
            Gym gym = gymRepository.findById(gymId).orElse(null);
            String status = gym == null ? "SUSPENDED" : gym.getStatus();
            cached = new CachedStatus(status, now + 30_000);
            cache.put(gymId, cached);
        }
        if (!"ACTIVE".equals(cached.status())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiError.of(403, "ACCOUNT_SUSPENDED", "This gym account is suspended. Contact support.")));
            return false;
        }
        return true;
    }

    public void evict(String gymId) {
        cache.remove(gymId);
    }

    private record CachedStatus(String status, long expiresAt) {
    }
}
