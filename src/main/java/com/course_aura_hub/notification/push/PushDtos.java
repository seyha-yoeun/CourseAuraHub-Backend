package com.course_aura_hub.notification.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Shapes for browser push. A subscription body is what the browser's PushSubscription.toJSON() returns. */
public final class PushDtos {

    private PushDtos() {
    }

    public record Keys(
            @NotBlank(message = "p256dh key is required") @Size(max = 200) String p256dh,
            @NotBlank(message = "auth secret is required") @Size(max = 100) String auth
    ) {
    }

    public record SubscribeRequest(
            @NotBlank(message = "endpoint is required") @Size(max = 1000, message = "endpoint is too long") String endpoint,
            @NotNull(message = "keys are required") @Valid Keys keys
    ) {
    }

    public record UnsubscribeRequest(@NotBlank(message = "endpoint is required") String endpoint) {
    }

    /** @param enabled whether this server can send push at all */
    public record VapidKeyResponse(String publicKey, boolean enabled) {
    }

    public record SubscriptionStatus(boolean subscribed, long devices) {
    }

    public record Preference(String type, boolean enabled) {
    }

    public record UpdatePreferences(@NotNull @Size(max = 50) List<@Valid Preference> preferences) {
    }
}
