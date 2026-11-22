package com.course_aura_hub.gift.security;

import com.course_aura_hub.auth.security.SecureTokenGenerator;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessCodeCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("x".repeat(32).getBytes());

    @Test
    void encryptsAndDecryptsACode_withAFreshIvEachTime() {
        AccessCodeCipher cipher = new AccessCodeCipher(KEY, new SecureTokenGenerator());

        String first = cipher.encrypt("ABCD-EFGH-JKMN-PQRS").orElseThrow();
        String second = cipher.encrypt("ABCD-EFGH-JKMN-PQRS").orElseThrow();

        assertThat(first).isNotEqualTo(second).doesNotContain("ABCD");
        assertThat(cipher.decrypt(first)).isEqualTo("ABCD-EFGH-JKMN-PQRS");
    }

    @Test
    void withoutAKey_codesStayHashOnly() {
        AccessCodeCipher cipher = new AccessCodeCipher("", new SecureTokenGenerator());

        assertThat(cipher.isAvailable()).isFalse();
        assertThat(cipher.encrypt("ABCD-EFGH-JKMN-PQRS")).isEmpty();
    }

    @Test
    void aDifferentKeyCannotDecrypt() {
        String stored = new AccessCodeCipher(KEY, new SecureTokenGenerator()).encrypt("ABCD-EFGH-JKMN-PQRS").orElseThrow();

        assertThatThrownBy(() -> new AccessCodeCipher(OTHER_KEY, new SecureTokenGenerator()).decrypt(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeysOfTheWrongSize() {
        assertThatThrownBy(() -> new AccessCodeCipher(Base64.getEncoder().encodeToString(new byte[16]), new SecureTokenGenerator()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
