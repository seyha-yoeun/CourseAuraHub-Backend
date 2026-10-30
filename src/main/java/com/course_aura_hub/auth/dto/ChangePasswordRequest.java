package com.course_aura_hub.auth.dto;

import com.course_aura_hub.auth.validator.PasswordStrength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to change the password of the signed-in account")
public record ChangePasswordRequest(

        @Schema(description = "Current password")
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(description = "New password (min 8 chars, upper, lower, digit, special char)")
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @PasswordStrength
        String newPassword
) {}
