package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Authenticator code (or a recovery code where noted)")
public record MfaCodeRequest(

        @Schema(description = "6-digit authenticator code", example = "123456")
        @NotBlank(message = "Code is required")
        @Size(max = 32)
        String code
) {}
