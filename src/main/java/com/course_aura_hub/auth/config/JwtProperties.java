package com.course_aura_hub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds directly to the 'jwt' configuration prefix defined in application.yml.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiration,
        long refreshTokenExpiration
) {}
