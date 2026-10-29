package com.course_aura_hub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Tunables for verification codes, password reset, lockout, sessions, 2FA and client-IP resolution.
 * Binds to 'app.security.auth'.
 */
@ConfigurationProperties(prefix = "app.security.auth")
public record AuthSecurityProperties(
        @DefaultValue EmailVerification emailVerification,
        @DefaultValue PasswordReset passwordReset,
        @DefaultValue Lockout lockout,
        @DefaultValue Session session,
        @DefaultValue Mfa mfa,
        @DefaultValue OAuth2 oauth2,
        @DefaultValue("7d") Duration tokenRetention,
        @DefaultValue List<String> trustedProxies
) {

    public AuthSecurityProperties {
        requirePositive(tokenRetention, "token-retention");
    }

    public record EmailVerification(
            @DefaultValue("6") int codeLength,
            @DefaultValue("10m") Duration codeTtl,
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("60s") Duration resendCooldown,
            @DefaultValue("5") int maxCodesPerHour
    ) {
        public EmailVerification {
            if (codeLength < 6 || codeLength > 10) {
                throw new IllegalArgumentException("app.security.auth.email-verification.code-length must be between 6 and 10");
            }
            requirePositive(codeTtl, "email-verification.code-ttl");
            if (maxAttempts < 1 || maxCodesPerHour < 1) {
                throw new IllegalArgumentException("email verification attempt and hourly limits must be positive");
            }
        }
    }

    public record PasswordReset(
            @DefaultValue("30m") Duration tokenTtl,
            @DefaultValue("60s") Duration requestCooldown,
            @DefaultValue("5") int maxRequestsPerHour,
            @DefaultValue("http://localhost:3000/auth/reset-password") String resetUrl
    ) {
        public PasswordReset {
            requirePositive(tokenTtl, "password-reset.token-ttl");
            if (maxRequestsPerHour < 1) {
                throw new IllegalArgumentException("password-reset.max-requests-per-hour must be positive");
            }
        }
    }

    public record Lockout(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15m") Duration lockDuration
    ) {
        public Lockout {
            if (maxFailedAttempts < 1) {
                throw new IllegalArgumentException("lockout.max-failed-attempts must be positive");
            }
            requirePositive(lockDuration, "lockout.lock-duration");
        }
    }

    public record Session(
            @DefaultValue("10") int maxActivePerUser,
            @DefaultValue("30d") Duration rememberMeTtl,
            @DefaultValue("10s") Duration reuseGracePeriod
    ) {
        public Session {
            if (maxActivePerUser < 1) {
                throw new IllegalArgumentException("session.max-active-per-user must be positive");
            }
            requirePositive(rememberMeTtl, "session.remember-me-ttl");
        }
    }

    public record Mfa(
            @DefaultValue("CourseAuraHub") String issuer,
            @DefaultValue("") String encryptionKey,
            @DefaultValue("5m") Duration challengeTtl,
            @DefaultValue("5") int maxChallengeAttempts,
            @DefaultValue("10") int recoveryCodeCount
    ) {
        public Mfa {
            requirePositive(challengeTtl, "mfa.challenge-ttl");
            if (maxChallengeAttempts < 1 || recoveryCodeCount < 1) {
                throw new IllegalArgumentException("mfa attempt and recovery code limits must be positive");
            }
        }
    }

    public record OAuth2(
            // Facebook does not expose a verification flag, so its emails are untrusted unless opted in
            @DefaultValue("false") boolean trustFacebookEmail
    ) {
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("app.security.auth." + name + " must be a positive duration");
        }
    }
}
