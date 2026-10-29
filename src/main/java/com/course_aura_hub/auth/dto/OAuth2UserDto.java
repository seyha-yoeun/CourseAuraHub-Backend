package com.course_aura_hub.auth.dto;

import com.course_aura_hub.auth.model.AuthProvider;
import lombok.Builder;

/**
 * Provider profile normalized by an {@code OAuth2UserInfoExtractor}.
 *
 * @param emailVerified true only when the provider itself vouches that the user controls {@code email}
 */
@Builder
public record OAuth2UserDto(
        String providerId,
        String email,
        boolean emailVerified,
        String firstName,
        String lastName,
        String imageUrl,
        AuthProvider provider
) {
}
