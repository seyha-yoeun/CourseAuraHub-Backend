package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Security status of an account, as seen by administrators")
public record AdminUserStatusResponse(
        UUID userId,
        String email,
        boolean enabled,
        boolean emailVerified,
        boolean mfaEnabled,
        Instant lockedUntil,
        int failedLoginAttempts
) {}
