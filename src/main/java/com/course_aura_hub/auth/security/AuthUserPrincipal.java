package com.course_aura_hub.auth.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.UUID;

/**
 * Spring Security principal carrying what {@link JwtAuthFilter} needs to reject revoked tokens
 * (token version) and what controllers need to act on the caller (user id, current session id).
 */
@Getter
public class AuthUserPrincipal extends User {

    private final UUID userId;
    private final int tokenVersion;
    private final UUID sessionId;

    public AuthUserPrincipal(UUID userId, String username, String password, boolean enabled, boolean accountNonLocked,
                             Collection<? extends GrantedAuthority> authorities, int tokenVersion, UUID sessionId) {
        super(username, password, enabled, true, true, accountNonLocked, authorities);
        this.userId = userId;
        this.tokenVersion = tokenVersion;
        this.sessionId = sessionId;
    }

    public AuthUserPrincipal withSessionId(UUID newSessionId) {
        return new AuthUserPrincipal(userId, getUsername(), "", isEnabled(), isAccountNonLocked(),
                getAuthorities(), tokenVersion, newSessionId);
    }
}
