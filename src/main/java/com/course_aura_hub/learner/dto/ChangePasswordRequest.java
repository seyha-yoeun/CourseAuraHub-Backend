package com.course_aura_hub.learner.dto;

import com.course_aura_hub.auth.validator.PasswordStrength;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @PasswordStrength
        String newPassword
) {
}
