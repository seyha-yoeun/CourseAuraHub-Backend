package com.course_aura_hub.settings.dto;

public record PlatformSettingsResponse(
        String platformName,
        String platformTagline,
        String currency,
        String defaultLanguage,
        NotificationPreferences notifications,
        ProfileResponse profile
) {
}
