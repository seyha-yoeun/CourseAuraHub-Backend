package com.course_aura_hub.telegram.dto;

import jakarta.validation.constraints.NotNull;

public record TelegramPreferenceRequest(@NotNull Boolean enabled) {
}
