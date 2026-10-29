package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to confirm an email address with the emailed one-time code")
public record VerifyEmailRequest(

        @Schema(description = "Email address used at registration", example = "john.doe@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 150, message = "Email cannot exceed 150 characters")
        String email,

        @Schema(description = "Numeric verification code from the email", example = "482913")
        @NotBlank(message = "Verification code is required")
        @Pattern(regexp = "\\d{6,10}", message = "Verification code must contain only digits")
        String code
) {}
