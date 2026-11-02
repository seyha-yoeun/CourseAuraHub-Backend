package com.course_aura_hub.common.security;

import com.course_aura_hub.auth.security.AuthUserPrincipal;

import java.util.UUID;

/** Who is making a request, so services can decide access without touching HTTP or security types. */
public record Actor(UUID userId, String email, boolean admin) {

    public static Actor of(AuthUserPrincipal principal) {
        boolean admin = principal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new Actor(principal.getUserId(), principal.getUsername(), admin);
    }
}
