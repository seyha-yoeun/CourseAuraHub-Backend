package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned for every registration and resend request, whether or not the email exists.
 */
@Schema(description = "Email verification is required before the account can sign in")
public record VerificationRequiredResponse(

        @Schema(description = "Normalized email address the code was sent to (if eligible)", example = "john.doe@example.com")
        String email,

        @Schema(description = "Always true for this response", example = "true")
        boolean verificationRequired,

        @Schema(description = "Number of digits in the verification code", example = "6")
        int codeLength,

        @Schema(description = "Seconds a newly issued code stays valid", example = "600")
        long codeExpiresInSeconds,

        @Schema(description = "Seconds clients should wait before offering 'resend'", example = "60")
        long resendCooldownSeconds
) {}
