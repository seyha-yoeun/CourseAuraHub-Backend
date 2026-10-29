package com.course_aura_hub.auth.security.mfa;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.exception.MfaUnavailableException;
import com.course_aura_hub.auth.security.SecureTokenGenerator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption of TOTP secrets at rest (they must be recoverable, so they cannot be hashed).
 * The key comes from MFA_ENCRYPTION_KEY; without it 2FA enrollment is unavailable.
 */
@Component
public class MfaSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte VERSION = 1;

    private final SecretKeySpec key;
    private final SecureTokenGenerator randomSource;

    public MfaSecretCipher(AuthSecurityProperties properties, SecureTokenGenerator randomSource) {
        this.randomSource = randomSource;
        String configuredKey = properties.mfa().encryptionKey();
        if (!StringUtils.hasText(configuredKey)) {
            this.key = null;
            return;
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configuredKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("app.security.auth.mfa.encryption-key must be Base64 encoded");
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.auth.mfa.encryption-key must decode to exactly 32 bytes (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isAvailable() {
        return key != null;
    }

    public String encrypt(byte[] plaintext) {
        requireKey();
        try {
            byte[] iv = randomSource.randomBytes(IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(new byte[]{VERSION});
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer out = ByteBuffer.allocate(1 + IV_BYTES + ciphertext.length);
            out.put(VERSION).put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(out.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt MFA secret", ex);
        }
    }

    public byte[] decrypt(String encoded) {
        requireKey();
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length <= 1 + IV_BYTES || data[0] != VERSION) {
                throw new IllegalStateException("Unsupported MFA secret format");
            }
            byte[] iv = Arrays.copyOfRange(data, 1, 1 + IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(new byte[]{VERSION});
            return cipher.doFinal(data, 1 + IV_BYTES, data.length - 1 - IV_BYTES);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to decrypt MFA secret", ex);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new MfaUnavailableException();
        }
    }
}
