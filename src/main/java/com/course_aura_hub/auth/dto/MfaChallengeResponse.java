package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Returned by login instead of tokens when the account has two-factor authentication enabled.
 */
@Schema(description = "Password accepted; a second factor is required to finish signing in")
public record MfaChallengeResponse(

        @Schema(description = "Always true for this response", example = "true")
        boolean mfaRequired,

        @Schema(description = "Opaque single-use challenge token to send to POST /api/v1/auth/mfa/verify")
        String mfaToken,

        @Schema(description = "Accepted second factors", example = "[\"TOTP\", \"RECOVERY_CODE\"]")
        List<String> methods,

        @Schema(description = "Seconds until the challenge expires", example = "300")
        long expiresInSeconds
) {}
