package com.course_aura_hub.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record SettingsUpdateRequest(
        @Size(max = 150, message = "Platform name must not exceed 150 characters")
        String platformName,

        @Size(max = 255, message = "Platform tagline must not exceed 255 characters")
        String platformTagline,

        @Size(max = 10, message = "Currency code must not exceed 10 characters")
        String currency,

        @Size(max = 10, message = "Language code must not exceed 10 characters")
        String defaultLanguage,

        @Valid
        NotificationPreferences notifications,

        @Valid
        ProfileUpdateRequest profile
) {
}
