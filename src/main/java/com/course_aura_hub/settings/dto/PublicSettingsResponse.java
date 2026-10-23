package com.course_aura_hub.settings.dto;

public record PublicSettingsResponse(
        String platformName,
        String platformTagline,
        String currency,
        String defaultLanguage
) {
}
