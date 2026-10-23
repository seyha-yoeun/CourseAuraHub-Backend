package com.course_aura_hub.settings.dto;

public record ProfileResponse(
        String name,
        String email,
        String role,
        String avatar,
        String bio,
        String website,
        String twitter
) {
}
