package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload required to refresh JWT access token")
public record RefreshTokenRequest(

        @Schema(description = "Valid JWT Refresh Token", example = "eyJhbGciOiJIUzI1NiIsIn...")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
