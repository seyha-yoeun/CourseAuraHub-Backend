package com.course_aura_hub.gift.security;

import com.course_aura_hub.auth.security.SecureTokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * AES-256-GCM encryption of admin-issued access codes, so admins can see a full code again later.
 * Redemption never uses this: codes are still looked up by their hash.
 *
 * The key is app.access-codes.encryption-key (ACCESS_CODE_ENCRYPTION_KEY, falling back to MFA_ENCRYPTION_KEY).
 * The associated data binds each ciphertext to this purpose, so sharing a key with the MFA cipher cannot let
 * one kind of ciphertext be accepted as the other. Without a key, codes are stored hash-only as before.
 */
@Component
public class AccessCodeCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte VERSION = 1;
    private static final byte[] PURPOSE = "course-aura-hub/access-code/v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;
    private final SecureTokenGenerator randomSource;

    public AccessCodeCipher(@Value("${app.access-codes.encryption-key:}") String configuredKey,
                            SecureTokenGenerator randomSource) {
        this.randomSource = randomSource;
        if (!StringUtils.hasText(configuredKey)) {
            this.key = null;
            return;
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configuredKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("app.access-codes.encryption-key must be Base64 encoded");
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "app.access-codes.encryption-key must decode to exactly 32 bytes (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isAvailable() {
        return key != null;
    }

    /** Encrypts a code, or returns empty when no key is configured (the code then stays hash-only). */
    public Optional<String> encrypt(String code) {
        if (key == null) {
            return Optional.empty();
        }
        try {
            byte[] iv = randomSource.randomBytes(IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData());
            byte[] ciphertext = cipher.doFinal(code.getBytes(StandardCharsets.UTF_8));
            ByteBuffer out = ByteBuffer.allocate(1 + IV_BYTES + ciphertext.length);
            out.put(VERSION).put(iv).put(ciphertext);
            return Optional.of(Base64.getEncoder().encodeToString(out.array()));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt access code", ex);
        }
    }

    /** Decrypts a stored code. Fails if no key is configured or the key changed since it was stored. */
    public String decrypt(String encoded) {
        if (key == null) {
            throw new IllegalStateException("Access code encryption key is not configured");
        }
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length <= 1 + IV_BYTES || data[0] != VERSION) {
                throw new IllegalStateException("Unsupported access code ciphertext format");
            }
            byte[] iv = Arrays.copyOfRange(data, 1, 1 + IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData());
            byte[] plain = cipher.doFinal(data, 1 + IV_BYTES, data.length - 1 - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt access code", ex);
        }
    }

    private static byte[] associatedData() {
        return ByteBuffer.allocate(1 + PURPOSE.length).put(VERSION).put(PURPOSE).array();
    }
}
