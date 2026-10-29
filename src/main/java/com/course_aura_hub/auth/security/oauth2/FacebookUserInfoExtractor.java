package com.course_aura_hub.auth.security.oauth2;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.model.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.course_aura_hub.auth.security.oauth2.OAuth2UserInfoExtractor.orDefault;
import static com.course_aura_hub.auth.security.oauth2.OAuth2UserInfoExtractor.stringAttribute;

/**
 * Facebook's Graph API exposes no verification flag, so its email is treated as unverified
 * unless 'app.security.auth.oauth2.trust-facebook-email' is explicitly enabled.
 */
@Component
@RequiredArgsConstructor
public class FacebookUserInfoExtractor implements OAuth2UserInfoExtractor {

    private final AuthSecurityProperties properties;

    @Override
    public String registrationId() {
        return "facebook";
    }

    @Override
    public OAuth2UserDto extract(Map<String, Object> attributes) {
        String id = stringAttribute(attributes, "id");
        String email = stringAttribute(attributes, "email");
        boolean emailVerified = email != null && properties.oauth2().trustFacebookEmail();
        if (email == null && id != null) {
            email = id + "@facebook.com";
        }

        String pictureUrl = null;
        if (attributes.get("picture") instanceof Map<?, ?> picMap
                && picMap.get("data") instanceof Map<?, ?> dataMap
                && dataMap.get("url") != null) {
            pictureUrl = dataMap.get("url").toString();
        }

        return OAuth2UserDto.builder()
                .providerId(id)
                .email(email)
                .emailVerified(emailVerified)
                .firstName(orDefault(stringAttribute(attributes, "first_name"), "Facebook"))
                .lastName(orDefault(stringAttribute(attributes, "last_name"), "User"))
                .imageUrl(pictureUrl)
                .provider(AuthProvider.FACEBOOK)
                .build();
    }
}
