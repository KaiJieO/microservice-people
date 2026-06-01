package com.microservice.people.security;

import com.microservice.people.exception.UnauthorizedAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
    }

    public static void assertSelfOrAdmin(String ownerId) {
        if (!isAdmin() && (ownerId == null || !ownerId.equals(currentUserId()))) {
            throw new UnauthorizedAccessException("You do not have permission to access this resource");
        }
    }
}
