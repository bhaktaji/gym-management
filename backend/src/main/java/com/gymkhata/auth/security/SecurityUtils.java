package com.gymkhata.auth.security;

import com.gymkhata.auth.model.User;
import com.gymkhata.common.error.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The gymId is ALWAYS resolved from the authenticated JWT principal,
 * never from request input.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw BusinessException.forbidden("UNAUTHENTICATED", "Not authenticated");
        }
        return user;
    }

    public static String currentUserId() {
        return currentUser().getId();
    }

    public static String currentGymId() {
        User user = currentUser();
        if (user.getGymId() == null) {
            throw BusinessException.forbidden("NO_GYM", "No gym context for this account");
        }
        return user.getGymId();
    }

    public static boolean isSuperAdmin() {
        return User.ROLE_SUPER_ADMIN.equals(currentUser().getRole());
    }
}
