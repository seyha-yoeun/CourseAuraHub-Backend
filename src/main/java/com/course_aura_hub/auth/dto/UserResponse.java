package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Authenticated user profile information")
public record UserResponse(

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

        @Schema(description = "Whether the account email address has been verified", example = "true")
        boolean emailVerified,

        @Schema(description = "Whether two-factor authentication is enabled", example = "false")
        boolean mfaEnabled
) {
}
