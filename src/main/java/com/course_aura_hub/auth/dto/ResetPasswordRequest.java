package com.course_aura_hub.auth.dto;

import com.course_aura_hub.auth.validator.PasswordStrength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to complete a password reset with the emailed token")
public record ResetPasswordRequest(

        @Schema(description = "Reset token from the emailed link")
        @NotBlank(message = "Reset token is required")
        @Size(max = 200, message = "Reset token is invalid")
        String token,

        @Schema(description = "New password (min 8 chars, upper, lower, digit, special char)", example = "N3w-P@ssword")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @PasswordStrength
        String password,

        @Schema(description = "Optional confirmation; must match password when present")
        String confirmPassword
) {

    @Schema(hidden = true)
    @AssertTrue(message = "Passwords do not match")
    public boolean isConfirmationMatching() {
        return confirmPassword == null || confirmPassword.equals(password);
    }
}
