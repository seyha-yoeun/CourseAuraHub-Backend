package com.course_aura_hub.auth.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographically secure random values for OTPs, reset links, challenges and recovery codes.
 */
@Component
public class SecureTokenGenerator {

    private static final char[] RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();

    private final SecureRandom random = new SecureRandom();

    /** Uniformly distributed decimal code; leading zeros are preserved. */
    public String numericCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /** URL-safe token carrying {@code byteLength} bytes of entropy. */
    public String urlSafeToken(int byteLength) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(byteLength));
    }

    /** Human-friendly code such as {@code k7d2m-q9x4z} (about 49 bits), without look-alike characters. */
    public String recoveryCode() {
        StringBuilder code = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                code.append('-');
            }
            code.append(RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return code.toString();
    }

    public byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }
}
