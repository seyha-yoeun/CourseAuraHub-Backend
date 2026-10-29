package com.course_aura_hub.auth.security.oauth2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the user-info of non-OIDC providers and, for GitHub, adds the primary verified email from
 * the {@code /user/emails} API (granted by the {@code user:email} scope). Without it the account
 * email cannot be trusted for linking.
 */
@Slf4j
@Component
public class ProviderEmailEnrichingOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    public static final String VERIFIED_EMAIL_ATTRIBUTE = "verified_email";
    public static final String EMAIL_VERIFIED_ATTRIBUTE = "email_verified";
    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final RestClient restClient;

    public ProviderEmailEnrichingOAuth2UserService() {
        this(new DefaultOAuth2UserService(), RestClient.create());
    }

    ProviderEmailEnrichingOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate, RestClient restClient) {
        this.delegate = delegate;
        this.restClient = restClient;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        if (!"github".equalsIgnoreCase(userRequest.getClientRegistration().getRegistrationId())) {
            return user;
        }

        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        Optional<String> verifiedEmail = fetchGithubVerifiedEmail(userRequest.getAccessToken().getTokenValue());
        attributes.put(EMAIL_VERIFIED_ATTRIBUTE, verifiedEmail.isPresent());
        verifiedEmail.ifPresent(email -> attributes.put(VERIFIED_EMAIL_ATTRIBUTE, email));

        String nameAttribute = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(user.getAuthorities(), attributes, nameAttribute);
    }

    Optional<String> fetchGithubVerifiedEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = restClient.get()
                    .uri(GITHUB_EMAILS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return selectVerifiedEmail(emails);
        } catch (RuntimeException ex) {
            // Treat the email as unverified; never log the access token
            log.warn("Could not load verified GitHub emails: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static Optional<String> selectVerifiedEmail(List<Map<String, Object>> emails) {
        if (emails == null) {
            return Optional.empty();
        }
        Optional<String> primary = emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("verified")) && Boolean.TRUE.equals(e.get("primary")))
                .map(e -> String.valueOf(e.get("email")))
                .findFirst();
        if (primary.isPresent()) {
            return primary;
        }
        return emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("verified")) && e.get("email") != null)
                .map(e -> String.valueOf(e.get("email")))
                .filter(email -> !email.endsWith("@users.noreply.github.com"))
                .findFirst();
    }
}
