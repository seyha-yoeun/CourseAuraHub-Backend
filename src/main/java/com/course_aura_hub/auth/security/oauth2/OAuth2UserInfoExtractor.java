package com.course_aura_hub.auth.security.oauth2;

import com.course_aura_hub.auth.dto.OAuth2UserDto;

import java.util.Map;

/**
 * Strategy that normalizes one provider's attributes, including whether the provider
 * vouches for the email address.
 */
public interface OAuth2UserInfoExtractor {

    /** Spring Security client registration id, e.g. {@code google}. */
    String registrationId();

    OAuth2UserDto extract(Map<String, Object> attributes);

    static String stringAttribute(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        return value == null ? null : value.toString();
    }

    static boolean booleanAttribute(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
