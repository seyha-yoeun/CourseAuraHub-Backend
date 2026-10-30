package com.course_aura_hub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Set;

/**
 * Binds directly to the 'app.cookie' configuration prefix defined in application.yml.
 */
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(
        boolean secure,
        String sameSite,
        String domain,
        String accessTokenName,
        String refreshTokenName,
        String path,
        long accessTokenMaxAge,
        long refreshTokenMaxAge,
        boolean httpOnly
) {

    private static final Set<String> SAME_SITE_VALUES = Set.of("lax", "strict", "none");

    public CookieProperties {
        String normalizedSameSite = sameSite == null ? "" : sameSite.trim().toLowerCase(Locale.ROOT);
        if (!SAME_SITE_VALUES.contains(normalizedSameSite)) {
            throw new IllegalArgumentException("app.cookie.same-site must be one of lax, strict or none");
        }
        // Browsers reject SameSite=None cookies that are not Secure
        if ("none".equals(normalizedSameSite) && !secure) {
            throw new IllegalArgumentException("app.cookie.same-site=none requires app.cookie.secure=true");
        }
        if (!httpOnly) {
            throw new IllegalArgumentException("app.cookie.http-only must stay true; auth cookies must not be readable by scripts");
        }
    }
}
