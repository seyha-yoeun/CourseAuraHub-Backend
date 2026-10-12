package com.course_aura_hub.auth.dto;

import com.course_aura_hub.auth.validator.PasswordStrength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload required for local user registration")
public record RegisterRequest(

        @Schema(description = "User's first name", example = "John")
        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
        String firstName,

        @Schema(description = "User's last name", example = "Doe")
        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
        String lastName,

        @Schema(description = "User's email address", example = "john.doe@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 150, message = "Email cannot exceed 150 characters")
        String email,

        @Schema(description = "User's password (min 8 chars, upper, lower, digit, special char)", example = "P@ssword123")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @PasswordStrength
        String password
) {}
