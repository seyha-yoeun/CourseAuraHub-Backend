package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Proof required to turn off two-factor authentication")
public record MfaDisableRequest(

        @Schema(description = "Authenticator code or an unused recovery code")
        @NotBlank(message = "Code is required")
        @Size(max = 32)
        String code,

        @Schema(description = "Current password; required for accounts that have one")
        String password
) {}
