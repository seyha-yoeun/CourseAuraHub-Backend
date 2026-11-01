package com.course_aura_hub.membercard.dto;

import jakarta.validation.constraints.NotBlank;

public record MemberCardStatusRequest(
        @NotBlank(message = "Status is required")
        String status
) {
}
