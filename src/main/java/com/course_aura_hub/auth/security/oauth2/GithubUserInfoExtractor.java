package com.course_aura_hub.auth.security.oauth2;

import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.model.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

import static com.course_aura_hub.auth.security.oauth2.OAuth2UserInfoExtractor.booleanAttribute;
import static com.course_aura_hub.auth.security.oauth2.OAuth2UserInfoExtractor.stringAttribute;

/**
 * GitHub: the profile {@code email} is just the public email and is not proof of ownership.
 * {@link ProviderEmailEnrichingOAuth2UserService} adds the verified primary email from /user/emails.
 */
@Component
public class GithubUserInfoExtractor implements OAuth2UserInfoExtractor {

    @Override
    public String registrationId() {
        return "github";
    }

    @Override
    public OAuth2UserDto extract(Map<String, Object> attributes) {
        String login = stringAttribute(attributes, "login");
        String verifiedEmail = stringAttribute(attributes, ProviderEmailEnrichingOAuth2UserService.VERIFIED_EMAIL_ATTRIBUTE);
        boolean emailVerified = verifiedEmail != null
                && booleanAttribute(attributes, ProviderEmailEnrichingOAuth2UserService.EMAIL_VERIFIED_ATTRIBUTE);

        String email = emailVerified ? verifiedEmail : stringAttribute(attributes, "email");
        if (email == null && login != null) {
            email = login.toLowerCase(Locale.ROOT) + "@users.noreply.github.com";
        }

        String firstName = login != null ? login : "GitHub";
        String lastName = "User";
        String name = stringAttribute(attributes, "name");
        if (name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+", 2);
            firstName = parts[0];
            if (parts.length > 1) {
                lastName = parts[1];
            }
        }

        return OAuth2UserDto.builder()
                .providerId(stringAttribute(attributes, "id"))
                .email(email)
                .emailVerified(emailVerified)
                .firstName(firstName)
                .lastName(lastName)
                .imageUrl(stringAttribute(attributes, "avatar_url"))
                .provider(AuthProvider.GITHUB)
                .build();
    }
}
