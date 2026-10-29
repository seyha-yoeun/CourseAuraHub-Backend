package com.course_aura_hub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-client limits for '/api/v1/auth/**'. Configured rules override the built-in defaults by name.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("50000") int maxTrackedClients,
        @DefaultValue Rule defaultRule,
        Map<String, Rule> rules
) {

    private static final Map<String, Rule> DEFAULT_RULES = Map.of(
            "register", new Rule("/api/v1/auth/register", 5, Duration.ofMinutes(1)),
            "login", new Rule("/api/v1/auth/login", 10, Duration.ofMinutes(1)),
            "refresh", new Rule("/api/v1/auth/refresh", 20, Duration.ofMinutes(1)),
            "logout", new Rule("/api/v1/auth/logout", 20, Duration.ofMinutes(1)),
            "verify-email", new Rule("/api/v1/auth/verify-email", 10, Duration.ofMinutes(1)),
            "resend-verification", new Rule("/api/v1/auth/verify-email/resend", 5, Duration.ofMinutes(1)),
            "forgot-password", new Rule("/api/v1/auth/forgot-password", 5, Duration.ofMinutes(1)),
            "reset-password", new Rule("/api/v1/auth/reset-password", 10, Duration.ofMinutes(1)),
            "mfa-verify", new Rule("/api/v1/auth/mfa/verify", 10, Duration.ofMinutes(1))
    );

    public RateLimitProperties {
        if (maxTrackedClients < 1) {
            throw new IllegalArgumentException("app.rate-limit.max-tracked-clients must be positive");
        }
        Map<String, Rule> merged = new LinkedHashMap<>(DEFAULT_RULES);
        if (rules != null) {
            merged.putAll(rules);
        }
        rules = Map.copyOf(merged);
    }

    public record Rule(
            String path,
            @DefaultValue("100") int capacity,
            @DefaultValue("1m") Duration period
    ) {
        public Rule {
            if (capacity < 1 || period == null || period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("rate limit rules need a positive capacity and period");
            }
        }
    }
}
