package com.course_aura_hub.telegram.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "The caller's Telegram connection")
public record TelegramStatusResponse(

        @Schema(description = "Whether this platform has a Telegram bot set up")
        boolean available,

        @Schema(description = "Whether the caller has connected a chat")
        boolean connected,

        @Schema(description = "Whether notifications are sent to the connected chat")
        boolean enabled,

        @Schema(description = "Name of the connected Telegram account")
        String telegramName,

        @Schema(description = "When the chat was connected")
        Instant linkedAt
) {
}
