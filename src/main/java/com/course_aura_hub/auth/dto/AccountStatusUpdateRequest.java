package com.course_aura_hub.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Administrative enable/disable of an account")
public record AccountStatusUpdateRequest(

        @Schema(description = "false disables the account and revokes all of its sessions", example = "false")
        @NotNull(message = "enabled is required")
        Boolean enabled
) {}
