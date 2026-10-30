package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

@Schema(description = "Second-factor proof for a pending login challenge")
public record MfaVerifyRequest(

        @Schema(description = "Challenge token from login. Optional when the mfa_challenge cookie is present (OAuth2 flow)")
        @Size(max = 200)
        String mfaToken,

        @Schema(description = "6-digit code from the authenticator app", example = "123456")
        @Size(max = 10)
        String code,

        @Schema(description = "Single-use recovery code, used instead of 'code'", example = "k7d2m-q9x4z")
        @Size(max = 32)
        String recoveryCode
) {

    @Schema(hidden = true)
    @AssertTrue(message = "Provide either an authenticator code or a recovery code")
    public boolean isSingleFactorProvided() {
        boolean hasCode = code != null && !code.isBlank();
        boolean hasRecovery = recoveryCode != null && !recoveryCode.isBlank();
        return hasCode ^ hasRecovery;
    }
}
