package com.course_aura_hub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Trusted browser origins. Credentials are allowed, so wildcards are rejected at startup.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @DefaultValue("http://localhost:3000") List<String> allowedOrigins
) {

    public CorsProperties {
        allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("app.cors.allowed-origins must list at least one trusted origin");
        }
        for (String origin : allowedOrigins) {
            if (origin.contains("*")) {
                throw new IllegalArgumentException(
                        "app.cors.allowed-origins must not contain wildcards when credentials are allowed: " + origin);
            }
        }
    }
}
