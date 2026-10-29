package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Authentication response containing JWT tokens and user metadata")
public record AuthResponse(

        @Schema(description = "JWT Access Token for Bearer authorization", example = "eyJhbGciOiJIUzI1NiIsIn...")
        String accessToken,

        @Schema(description = "JWT Refresh Token used to obtain a new access token", example = "eyJhbGciOiJIUzI1NiIsIn...")
        String refreshToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifespan in milliseconds", example = "900000")
        long expiresIn,

        @Schema(description = "User unique identifier")
        UUID userId,

        @Schema(description = "User email address", example = "john.doe@example.com")
        String email,

        @Schema(description = "User first name", example = "John")
        String firstName,

        @Schema(description = "User last name", example = "Doe")
        String lastName,

        @Schema(description = "User assigned role", example = "ROLE_USER")
        String role,

        @Schema(description = "User authentication provider", example = "LOCAL")
        String provider,

        @Schema(description = "Session (device) identifier; stable across refresh-token rotation")
        UUID sessionId,

        @Schema(description = "Refresh token lifespan in milliseconds", example = "604800000")
        long refreshExpiresIn
) {}
