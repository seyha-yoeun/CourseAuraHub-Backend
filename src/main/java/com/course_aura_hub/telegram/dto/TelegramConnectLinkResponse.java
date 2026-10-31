package com.course_aura_hub.telegram.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A one-time link that connects the Telegram chat that opens it")
public record TelegramConnectLinkResponse(

        @Schema(description = "Opens the bot with the connect token", example = "https://t.me/CourseAuraHubBot?start=token")
        String url,

        @Schema(description = "When the link stops working")
        Instant expiresAt
) {
}
