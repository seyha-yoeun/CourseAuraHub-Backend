package com.course_aura_hub.settings.service;

import com.course_aura_hub.settings.dto.PlatformSettingsResponse;
import com.course_aura_hub.settings.dto.PublicSettingsResponse;
import com.course_aura_hub.settings.dto.SettingsUpdateRequest;

public interface SettingsService {

    PlatformSettingsResponse getSettings(String email);

    PublicSettingsResponse getPublicSettings();

    PlatformSettingsResponse updateSettings(String email, SettingsUpdateRequest request);
}
