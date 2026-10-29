package com.course_aura_hub.auth.config;

import io.jsonwebtoken.io.Decoders;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Binds directly to the 'jwt' configuration prefix defined in application.yml.
 *
 * <p>The secret is validated at startup: the application refuses to start without a strong,
 * externally supplied HMAC key instead of silently padding a weak one.</p>
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiration,
        long refreshTokenExpiration
) {

    /** HS256 requires at least 256 bits of key material. */
    public static final int MIN_SECRET_BYTES = 32;
    private static final int MIN_DISTINCT_BYTES = 10;

    // Publicly documented sample keys that must never sign production tokens
    private static final Set<String> KNOWN_PUBLIC_SECRETS = Set.of(
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
            "1234567"
    );

    public JwtProperties {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException(
                    "jwt.secret is not configured. Set JWT_SECRET to at least 32 random bytes, "
                            + "for example the output of: openssl rand -base64 48");
        }
        if (KNOWN_PUBLIC_SECRETS.contains(secret.trim())) {
            throw new IllegalArgumentException(
                    "jwt.secret uses a publicly known sample value. Generate a new JWT_SECRET with: openssl rand -base64 48");
        }
        byte[] keyBytes = decodeSecret(secret);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "jwt.secret must provide at least " + MIN_SECRET_BYTES + " bytes of key material");
        }
        if (distinctByteCount(keyBytes) < MIN_DISTINCT_BYTES) {
            throw new IllegalArgumentException("jwt.secret is too predictable; use a randomly generated value");
        }
        if (accessTokenExpiration <= 0 || refreshTokenExpiration <= 0) {
            throw new IllegalArgumentException("jwt token expirations must be positive");
        }
    }

    /**
     * Key material used for signing. Base64 secrets are decoded; anything else is used as UTF-8,
     * matching how existing deployments derived the key.
     */
    public byte[] signingKeyBytes() {
        return decodeSecret(secret);
    }

    private static byte[] decodeSecret(String secret) {
        try {
            return Decoders.BASE64.decode(secret.trim());
        } catch (RuntimeException ex) {
            return secret.trim().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static long distinctByteCount(byte[] bytes) {
        boolean[] seen = new boolean[256];
        long count = 0;
        for (byte b : bytes) {
            if (!seen[b & 0xFF]) {
                seen[b & 0xFF] = true;
                count++;
            }
        }
        return count;
    }
}
