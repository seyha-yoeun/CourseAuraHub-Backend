package com.course_aura_hub.auth.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Misconfiguration must stop the application at startup rather than run insecurely.
 */
class ConfigurationValidationTest {

    private static final String STRONG_SECRET = "P7VIN9hw3AsKhz+tPEqcaZgeWAbmCy5ayl0gWY4s4EEaJ1wK6lgn+ir5Mn+qQC7I";

    @Test
    void jwtSecret_missingShortWeakOrPublic_isRejected() {
        assertThatThrownBy(() -> new JwtProperties(null, 1000, 1000)).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtProperties("  ", 1000, 1000)).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtProperties("1234567", 1000, 1000)).hasMessageContaining("publicly known");
        assertThatThrownBy(() -> new JwtProperties(
                "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", 1000, 1000))
                .hasMessageContaining("publicly known");
        assertThatThrownBy(() -> new JwtProperties("short-secret", 1000, 1000)).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new JwtProperties("a".repeat(64), 1000, 1000)).hasMessageContaining("predictable");
        assertThatThrownBy(() -> new JwtProperties(STRONG_SECRET, 0, 1000)).hasMessageContaining("positive");
    }

    @Test
    void jwtSecret_strongRandomValue_isAccepted() {
        assertThatCode(() -> new JwtProperties(STRONG_SECRET, 300000, 604800000)).doesNotThrowAnyException();
    }

    @Test
    void cookies_sameSiteNoneRequiresSecure_andHttpOnlyIsMandatory() {
        assertThatThrownBy(() -> cookies(false, "none", true)).hasMessageContaining("secure");
        assertThatThrownBy(() -> cookies(true, "sometimes", true)).hasMessageContaining("same-site");
        assertThatThrownBy(() -> cookies(true, "lax", false)).hasMessageContaining("http-only");
        assertThatCode(() -> cookies(true, "None", true)).doesNotThrowAnyException();
        assertThatCode(() -> cookies(false, "lax", true)).doesNotThrowAnyException();
    }

    @Test
    void cors_rejectsWildcardsAndEmptyLists() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*"))).hasMessageContaining("wildcards");
        assertThatThrownBy(() -> new CorsProperties(List.of("https://*.example.com"))).hasMessageContaining("wildcards");
        assertThatThrownBy(() -> new CorsProperties(List.of(" "))).hasMessageContaining("at least one");
        assertThatCode(() -> new CorsProperties(List.of("https://app.example.com"))).doesNotThrowAnyException();
    }

    private static CookieProperties cookies(boolean secure, String sameSite, boolean httpOnly) {
        return new CookieProperties(secure, sameSite, "", "access_token", "refresh_token", "/", 300, 604800, httpOnly);
    }
}
