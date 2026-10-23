package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload required for local user authentication")
public record LoginRequest(

        @Schema(description = "User's registered email address", example = "john.doe@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        String email,

        @Schema(description = "User's password", example = "P@ssword123")
        @NotBlank(message = "Password is required")
        String password,

        @Schema(description = "Extend refresh token expiry to 30 days (remember me)", example = "false")
        boolean rememberMe
) {}
