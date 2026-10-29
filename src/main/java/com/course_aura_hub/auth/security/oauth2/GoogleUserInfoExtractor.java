package com.course_aura_hub.auth.security.oauth2;

import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.model.AuthProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.course_aura_hub.auth.security.oauth2.OAuth2UserInfoExtractor.booleanAttribute;
import static com.course_aura_hub.auth.security.oauth2.OAuth2UserInfoExtractor.orDefault;
import static com.course_aura_hub.auth.security.oauth2.OAuth2UserInfoExtractor.stringAttribute;

/**
 * Google OIDC: trusts the signed {@code email_verified} ID-token claim.
 */
@Component
public class GoogleUserInfoExtractor implements OAuth2UserInfoExtractor {

    @Override
    public String registrationId() {
        return "google";
    }

    @Override
    public OAuth2UserDto extract(Map<String, Object> attributes) {
        return OAuth2UserDto.builder()
                .providerId(stringAttribute(attributes, "sub"))
                .email(stringAttribute(attributes, "email"))
                .emailVerified(booleanAttribute(attributes, "email_verified"))
                .firstName(orDefault(stringAttribute(attributes, "given_name"), "Google"))
                .lastName(orDefault(stringAttribute(attributes, "family_name"), "User"))
                .imageUrl(stringAttribute(attributes, "picture"))
                .provider(AuthProvider.GOOGLE)
                .build();
    }
}
