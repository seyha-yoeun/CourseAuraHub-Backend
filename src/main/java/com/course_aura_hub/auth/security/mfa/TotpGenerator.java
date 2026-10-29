package com.course_aura_hub.auth.security.mfa;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30-second steps, 6 digits): the parameters every mainstream
 * authenticator app supports.
 */
@Component
public class TotpGenerator {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    /** Accept one step either side to absorb clock drift. */
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final int[] POWERS_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    public long timeStep(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), PERIOD_SECONDS);
    }

    public String generate(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % POWERS_OF_TEN[DIGITS];
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA1 is not available", ex);
        }
    }

    /**
     * Returns the matched time step, or empty when the code is wrong or belongs to a step at or
     * before {@code lastUsedStep} (replay of an already accepted code).
     */
    public OptionalLong verify(byte[] secret, String code, Long lastUsedStep, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long currentStep = timeStep(now);
        byte[] submitted = code.getBytes(StandardCharsets.US_ASCII);
        for (long step = currentStep - ALLOWED_DRIFT_STEPS; step <= currentStep + ALLOWED_DRIFT_STEPS; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            byte[] expected = generate(secret, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, submitted)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }
}
