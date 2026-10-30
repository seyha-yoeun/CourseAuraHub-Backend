package com.course_aura_hub.auth.security.oauth2;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.exception.OAuth2LoginException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2UserInfoExtractorTest {

    private final OAuth2UserInfoExtractorRegistry registry = new OAuth2UserInfoExtractorRegistry(List.of(
            new GoogleUserInfoExtractor(),
            new GithubUserInfoExtractor(),
            new FacebookUserInfoExtractor(properties(false))
    ));

    @Test
    void google_trustsEmailVerifiedClaim() {
        OAuth2UserDto verified = registry.extract("google", Map.of("sub", "1", "email", "a@x.com", "email_verified", true));
        OAuth2UserDto unverified = registry.extract("google", Map.of("sub", "2", "email", "b@x.com", "email_verified", false));
        assertThat(verified.emailVerified()).isTrue();
        assertThat(unverified.emailVerified()).isFalse();
    }

    @Test
    void github_publicProfileEmailIsNotTrusted() {
        OAuth2UserDto dto = registry.extract("github", Map.of("id", 42, "login", "octo", "email", "public@x.com"));
        assertThat(dto.email()).isEqualTo("public@x.com");
        assertThat(dto.emailVerified()).isFalse();
    }

    @Test
    void github_usesVerifiedEmailFromEmailsApi() {
        OAuth2UserDto dto = registry.extract("github", Map.of(
                "id", 42, "login", "octo", "email", "public@x.com",
                ProviderEmailEnrichingOAuth2UserService.VERIFIED_EMAIL_ATTRIBUTE, "primary@x.com",
                ProviderEmailEnrichingOAuth2UserService.EMAIL_VERIFIED_ATTRIBUTE, true));
        assertThat(dto.email()).isEqualTo("primary@x.com");
        assertThat(dto.emailVerified()).isTrue();
        assertThat(dto.providerId()).isEqualTo("42");
    }

    @Test
    void github_selectsPrimaryVerifiedEmail() {
        Optional<String> selected = ProviderEmailEnrichingOAuth2UserService.selectVerifiedEmail(List.of(
                Map.of("email", "unverified@x.com", "verified", false, "primary", true),
                Map.of("email", "secondary@x.com", "verified", true, "primary", false)));
        assertThat(selected).contains("secondary@x.com");
        assertThat(ProviderEmailEnrichingOAuth2UserService.selectVerifiedEmail(List.of(
                Map.of("email", "unverified@x.com", "verified", false, "primary", true)))).isEmpty();
    }

    @Test
    void facebook_emailUntrustedUnlessConfigured() {
        Map<String, Object> attributes = Map.of("id", "99", "email", "fb@x.com");
        assertThat(registry.extract("facebook", attributes).emailVerified()).isFalse();
        assertThat(new FacebookUserInfoExtractor(properties(true)).extract(attributes).emailVerified()).isTrue();
        // Synthetic fallback addresses are never trusted
        assertThat(new FacebookUserInfoExtractor(properties(true)).extract(Map.of("id", "99")).emailVerified()).isFalse();
    }

    @Test
    void unknownProvider_isRejected() {
        assertThatThrownBy(() -> registry.extract("myspace", Map.of()))
                .isInstanceOf(OAuth2LoginException.class);
    }

    private static AuthSecurityProperties properties(boolean trustFacebook) {
        return new AuthSecurityProperties(
                new AuthSecurityProperties.EmailVerification(6, Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5),
                new AuthSecurityProperties.PasswordReset(Duration.ofMinutes(30), Duration.ofSeconds(60), 5, "http://localhost/reset"),
                new AuthSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new AuthSecurityProperties.Session(10, Duration.ofDays(30), Duration.ofSeconds(10)),
                new AuthSecurityProperties.Mfa("Test", "", Duration.ofMinutes(5), 5, 10),
                new AuthSecurityProperties.OAuth2(trustFacebook),
                Duration.ofDays(7),
                List.of()
        );
    }
}
