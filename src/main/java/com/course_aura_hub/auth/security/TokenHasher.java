package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.config.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Keyed hashing (HMAC-SHA256) for one-time secrets. A plain hash of a 6-digit code could be
 * brute-forced offline from a database dump; keying it with a server-side secret prevents that.
 *
 * <p>The key is derived from the JWT secret with a fixed label so the two uses never share a key.</p>
 */
@Component
public class TokenHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] DERIVATION_LABEL =
            "course-aura-hub/one-time-token-hash/v1".getBytes(StandardCharsets.UTF_8);
    private static final String SEPARATOR = "\u001F";

    private final SecretKeySpec key;

    public TokenHasher(JwtProperties jwtProperties) {
        byte[] derived = hmac(new SecretKeySpec(jwtProperties.signingKeyBytes(), HMAC_ALGORITHM), DERIVATION_LABEL);
        this.key = new SecretKeySpec(derived, HMAC_ALGORITHM);
    }

    /**
     * Hashes the parts joined with a unit separator, e.g. {@code hash(purpose, userId, code)}.
     * Binding purpose and owner into the input stops a hash being replayed for another purpose or user.
     */
    public String hash(String... parts) {
        byte[] digest = hmac(key, String.join(SEPARATOR, parts).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    public boolean matches(String expectedHash, String... parts) {
        if (expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                hash(parts).getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static byte[] hmac(SecretKeySpec key, byte[] input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(input);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }
}
