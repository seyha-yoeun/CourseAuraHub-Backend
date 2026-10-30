package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Two-factor authentication status of the signed-in account")
public record MfaStatusResponse(

        @Schema(description = "Whether 2FA is enforced at sign-in")
        boolean enabled,

        @Schema(description = "Whether an authenticator enrollment is waiting for confirmation")
        boolean enrollmentPending,

        @Schema(description = "Unused recovery codes left")
        long remainingRecoveryCodes,

        @Schema(description = "Whether the server is configured to support 2FA")
        boolean available
) {}
