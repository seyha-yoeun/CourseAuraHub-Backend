package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "An active sign-in session (one per device/browser)")
public record SessionResponse(

        @Schema(description = "Session identifier, usable with DELETE /api/v1/auth/sessions/{sessionId}")
        UUID sessionId,

        @Schema(description = "When the user signed in on this device")
        Instant createdAt,

        @Schema(description = "Last time the session refreshed its tokens")
        Instant lastUsedAt,

        @Schema(description = "When the current refresh token expires")
        Instant expiresAt,

        @Schema(description = "Client IP address recorded at the last refresh", example = "203.0.113.7")
        String ipAddress,

        @Schema(description = "Client user agent recorded at the last refresh")
        String userAgent,

        @Schema(description = "Whether 'remember me' was selected")
        boolean rememberMe,

        @Schema(description = "True for the session making this request")
        boolean current
) {}
