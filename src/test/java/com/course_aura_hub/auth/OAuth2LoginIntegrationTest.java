package com.course_aura_hub.auth;

import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.exception.OAuth2LoginException;
import com.course_aura_hub.auth.model.AuthProvider;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.OAuthIdentityRepository;
import com.course_aura_hub.auth.service.LoginOutcome;
import com.course_aura_hub.auth.service.OAuth2Service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises account provisioning and linking with provider profiles as the extractors produce them;
 * the provider round-trip itself is Spring Security's and is not re-tested here.
 */
class OAuth2LoginIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private OAuth2Service oAuth2Service;

    @Autowired
    private OAuthIdentityRepository identityRepository;

    @Test
    void newVerifiedIdentity_createsVerifiedAccountAndSession() {
        String email = uniqueEmail("google-new");
        LoginOutcome outcome = oAuth2Service.processOAuth2Login(profile(AuthProvider.GOOGLE, email, true));

        assertThat(outcome).isInstanceOf(LoginOutcome.Authenticated.class);
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getPassword()).isNull();
        assertThat(identityRepository.existsByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)).isTrue();
    }

    @Test
    void returningIdentity_signsIntoSameAccount() {
        String email = uniqueEmail("github-return");
        OAuth2UserDto profile = profile(AuthProvider.GITHUB, email, true);
        oAuth2Service.processOAuth2Login(profile);
        LoginOutcome second = oAuth2Service.processOAuth2Login(profile);

        assertThat(((LoginOutcome.Authenticated) second).auth().email()).isEqualTo(email);
        assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals(email))).hasSize(1);
    }

    @Test
    void unverifiedProviderEmail_mustNotLinkToExistingAccount() throws Exception {
        String email = uniqueEmail("victim");
        registerVerifiedUser(email);

        assertThatThrownBy(() -> oAuth2Service.processOAuth2Login(profile(AuthProvider.FACEBOOK, email, false)))
                .isInstanceOf(OAuth2LoginException.class)
                .extracting(ex -> ((OAuth2LoginException) ex).getReason())
                .isEqualTo(OAuth2LoginException.Reason.ACCOUNT_LINK_REQUIRED);

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identityRepository.existsByUserIdAndProvider(user.getId(), AuthProvider.FACEBOOK)).isFalse();
        loginSession(email, false);
    }

    @Test
    void verifiedProviderEmail_linksToVerifiedLocalAccount() throws Exception {
        String email = uniqueEmail("link");
        registerVerifiedUser(email);

        LoginOutcome outcome = oAuth2Service.processOAuth2Login(profile(AuthProvider.GOOGLE, email, true));

        assertThat(outcome).isInstanceOf(LoginOutcome.Authenticated.class);
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(identityRepository.existsByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)).isTrue();
        // Password login keeps working
        loginSession(email, false);
    }

    @Test
    void verifiedProviderEmail_claimsPendingRegistrationAndDropsSquatterPassword() throws Exception {
        String email = uniqueEmail("claim");
        register(email, "Squatter-P@ss1").andExpect(status().isAccepted());
        String code = emailSender.latestVerificationCode(email);

        oAuth2Service.processOAuth2Login(profile(AuthProvider.GOOGLE, email, true));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getPassword()).isNull();
        verifyEmail(email, code).andExpect(status().isBadRequest());
        login(email, "Squatter-P@ss1", false).andExpect(status().isUnauthorized());
    }

    @Test
    void secondIdentityFromSameProvider_isAConflict() {
        String email = uniqueEmail("conflict");
        oAuth2Service.processOAuth2Login(profile(AuthProvider.GITHUB, email, true));

        assertThatThrownBy(() -> oAuth2Service.processOAuth2Login(profile(AuthProvider.GITHUB, email, true)))
                .isInstanceOf(OAuth2LoginException.class)
                .extracting(ex -> ((OAuth2LoginException) ex).getReason())
                .isEqualTo(OAuth2LoginException.Reason.ACCOUNT_LINK_CONFLICT);
    }

    @Test
    void disabledOrLockedAccounts_areRejectedAfterOAuth() {
        String disabledEmail = uniqueEmail("oauth-disabled");
        OAuth2UserDto disabledProfile = profile(AuthProvider.GOOGLE, disabledEmail, true);
        oAuth2Service.processOAuth2Login(disabledProfile);
        User disabled = userRepository.findByEmail(disabledEmail).orElseThrow();
        disabled.setEnabled(false);
        userRepository.save(disabled);

        assertThatThrownBy(() -> oAuth2Service.processOAuth2Login(disabledProfile))
                .extracting(ex -> ((OAuth2LoginException) ex).getReason())
                .isEqualTo(OAuth2LoginException.Reason.ACCOUNT_UNAVAILABLE);

        String lockedEmail = uniqueEmail("oauth-locked");
        OAuth2UserDto lockedProfile = profile(AuthProvider.GOOGLE, lockedEmail, true);
        oAuth2Service.processOAuth2Login(lockedProfile);
        User locked = userRepository.findByEmail(lockedEmail).orElseThrow();
        locked.setLockedUntil(Instant.now().plus(5, ChronoUnit.MINUTES));
        userRepository.save(locked);

        assertThatThrownBy(() -> oAuth2Service.processOAuth2Login(lockedProfile))
                .extracting(ex -> ((OAuth2LoginException) ex).getReason())
                .isEqualTo(OAuth2LoginException.Reason.ACCOUNT_LOCKED);
    }

    @Test
    void missingEmail_isRejected() {
        assertThatThrownBy(() -> oAuth2Service.processOAuth2Login(profile(AuthProvider.GITHUB, null, false)))
                .extracting(ex -> ((OAuth2LoginException) ex).getReason())
                .isEqualTo(OAuth2LoginException.Reason.EMAIL_REQUIRED);
    }

    private static OAuth2UserDto profile(AuthProvider provider, String email, boolean verified) {
        return OAuth2UserDto.builder()
                .provider(provider)
                .providerId(provider.name().toLowerCase() + "-" + UUID.randomUUID())
                .email(email)
                .emailVerified(verified)
                .firstName("Oauth")
                .lastName("User")
                .build();
    }
}
