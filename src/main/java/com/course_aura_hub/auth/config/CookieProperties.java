package com.course_aura_hub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
) {}