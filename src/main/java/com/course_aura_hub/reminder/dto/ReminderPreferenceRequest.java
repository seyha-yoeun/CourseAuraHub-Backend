package com.course_aura_hub.reminder.dto;

import jakarta.validation.constraints.NotNull;

public record ReminderPreferenceRequest(
        @NotNull(message = "Say whether reminders are on")
        Boolean enabled
) {
}
