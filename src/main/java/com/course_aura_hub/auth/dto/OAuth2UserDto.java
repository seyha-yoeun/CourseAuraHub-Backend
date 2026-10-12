package com.course_aura_hub.auth.dto;

import com.course_aura_hub.auth.model.AuthProvider;
import lombok.Builder;

import java.util.Map;

@Builder
public record OAuth2UserDto(
        String providerId,
        String email,
        String firstName,
        String lastName,
        String imageUrl,
        AuthProvider provider,
        Map<String, Object> attributes
) {
    /**
     * Factory method to extract normalized user profile from raw OAuth2 attributes.
     */
    public static OAuth2UserDto fromProvider(String providerName, Map<String, Object> attributes) {
        String normalized = providerName.trim().toUpperCase();
        return switch (normalized) {
            case "GOOGLE" -> fromGoogle(attributes);
            case "GITHUB" -> fromGithub(attributes);
            case "FACEBOOK" -> fromFacebook(attributes);
            default -> throw new IllegalArgumentException("Unsupported OAuth2 provider: " + providerName);
        };
    }

    private static OAuth2UserDto fromGoogle(Map<String, Object> attributes) {
        String sub = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String givenName = (String) attributes.getOrDefault("given_name", "Google");
        String familyName = (String) attributes.getOrDefault("family_name", "User");
        String picture = (String) attributes.get("picture");

        return OAuth2UserDto.builder()
                .providerId(sub)
                .email(email)
                .firstName(givenName != null ? givenName : "Google")
                .lastName(familyName != null ? familyName : "User")
                .imageUrl(picture)
                .provider(AuthProvider.GOOGLE)
                .attributes(attributes)
                .build();
    }

    private static OAuth2UserDto fromGithub(Map<String, Object> attributes) {
        Object idObj = attributes.get("id");
        String id = idObj != null ? String.valueOf(idObj) : null;
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String login = (String) attributes.get("login");
        String avatarUrl = (String) attributes.get("avatar_url");

        String firstName = login != null ? login : "GitHub";
        String lastName = "User";
        if (name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+", 2);
            firstName = parts[0];
            if (parts.length > 1) {
                lastName = parts[1];
            }
        }

        // If email is null in public GitHub profile, construct a fallback or handle in service
        if (email == null && login != null) {
            email = login.toLowerCase() + "@users.noreply.github.com";
        }

        return OAuth2UserDto.builder()
                .providerId(id)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .imageUrl(avatarUrl)
                .provider(AuthProvider.GITHUB)
                .attributes(attributes)
                .build();
    }

    private static OAuth2UserDto fromFacebook(Map<String, Object> attributes) {
        String id = (String) attributes.get("id");
        String email = (String) attributes.get("email");
        String firstName = (String) attributes.getOrDefault("first_name", "Facebook");
        String lastName = (String) attributes.getOrDefault("last_name", "User");

        String pictureUrl = null;
        if (attributes.get("picture") instanceof Map<?, ?> picMap) {
            if (picMap.get("data") instanceof Map<?, ?> dataMap) {
                pictureUrl = (String) dataMap.get("url");
            }
        }

        if (email == null && id != null) {
            email = id + "@facebook.com";
        }

        return OAuth2UserDto.builder()
                .providerId(id)
                .email(email)
                .firstName(firstName != null ? firstName : "Facebook")
                .lastName(lastName != null ? lastName : "User")
                .imageUrl(pictureUrl)
                .provider(AuthProvider.FACEBOOK)
                .attributes(attributes)
                .build();
    }
}
