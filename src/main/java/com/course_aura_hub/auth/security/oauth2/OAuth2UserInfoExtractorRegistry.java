package com.course_aura_hub.auth.security.oauth2;

import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.exception.OAuth2LoginException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory selecting the extractor for a client registration id.
 */
@Component
public class OAuth2UserInfoExtractorRegistry {

    private final Map<String, OAuth2UserInfoExtractor> extractors;

    public OAuth2UserInfoExtractorRegistry(List<OAuth2UserInfoExtractor> extractors) {
        this.extractors = extractors.stream()
                .collect(Collectors.toUnmodifiableMap(e -> e.registrationId().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public OAuth2UserDto extract(String registrationId, Map<String, Object> attributes) {
        OAuth2UserInfoExtractor extractor = extractors.get(registrationId.trim().toLowerCase(Locale.ROOT));
        if (extractor == null) {
            throw new OAuth2LoginException(OAuth2LoginException.Reason.UNSUPPORTED_PROVIDER);
        }
        return extractor.extract(attributes);
    }
}
