package com.course_aura_hub.settings.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 150, message = "Profile name must not exceed 150 characters")
        String name,

        @Size(max = 1000, message = "Avatar URL must not exceed 1000 characters")
        String avatar,

        @Size(max = 1000, message = "Bio must not exceed 1000 characters")
        String bio,

        @Size(max = 500, message = "Website must not exceed 500 characters")
        String website,

        @Size(max = 100, message = "Twitter handle must not exceed 100 characters")
        String twitter
) {
}
