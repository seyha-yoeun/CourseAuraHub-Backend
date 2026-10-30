package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authenticator enrollment details. Shown once; confirm with a code to enable 2FA")
public record TotpSetupResponse(

        @Schema(description = "Base32 shared secret for manual entry")
        String secret,

        @Schema(description = "otpauth:// URI to render as a QR code")
        String otpauthUri,

        @Schema(description = "Code length", example = "6")
        int digits,

        @Schema(description = "Time step in seconds", example = "30")
        int periodSeconds
) {}
