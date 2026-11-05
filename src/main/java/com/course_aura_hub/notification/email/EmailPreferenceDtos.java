package com.course_aura_hub.notification.email;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Request and response shapes for notification email preferences. */
public final class EmailPreferenceDtos {

    private EmailPreferenceDtos() {
    }

    /** @param mode instant, digest or off */
    public record Preference(String type, String mode, String defaultMode) {
    }

    public record Change(@NotBlank String type, @NotBlank String mode) {
    }

    public record UpdateRequest(@NotEmpty @Valid List<Change> preferences) {
    }
}
