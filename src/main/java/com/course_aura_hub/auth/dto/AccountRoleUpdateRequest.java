package com.course_aura_hub.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountRoleUpdateRequest(
        @NotBlank(message = "Role is required")
        String role
) {
}
