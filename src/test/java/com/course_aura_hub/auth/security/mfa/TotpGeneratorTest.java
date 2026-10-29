package com.course_aura_hub.auth.security.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpGeneratorTest {

    // RFC 6238 Appendix B SHA-1 secret
    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private final TotpGenerator totp = new TotpGenerator();

    @Test
    void matchesRfc6238Vectors() {
        assertThat(totp.generate(SECRET, totp.timeStep(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(totp.generate(SECRET, totp.timeStep(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(totp.generate(SECRET, totp.timeStep(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(totp.generate(SECRET, totp.timeStep(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void acceptsOneStepOfDriftAndRejectsReplay() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = totp.timeStep(now);

        assertThat(totp.verify(SECRET, totp.generate(SECRET, step - 1), null, now)).hasValue(step - 1);
        assertThat(totp.verify(SECRET, totp.generate(SECRET, step + 1), null, now)).hasValue(step + 1);
        assertThat(totp.verify(SECRET, totp.generate(SECRET, step + 2), null, now)).isEmpty();
        assertThat(totp.verify(SECRET, totp.generate(SECRET, step), step, now)).isEmpty();
        assertThat(totp.verify(SECRET, "12345", null, now)).isEmpty();
        assertThat(totp.verify(SECRET, null, null, now)).isEmpty();
    }

    @Test
    void base32RoundTrips() {
        byte[] data = {0, 1, 2, (byte) 0xFF, 42, 17, 99, (byte) 0x80, 5, 6};
        assertThat(Base32.decode(Base32.encode(data))).isEqualTo(data);
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
    }
}
