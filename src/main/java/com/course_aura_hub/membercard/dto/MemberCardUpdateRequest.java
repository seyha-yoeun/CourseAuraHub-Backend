package com.course_aura_hub.membercard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberCardUpdateRequest(
        @NotBlank(message = "Holder name is required")
        @Size(max = 150, message = "Holder name must not exceed 150 characters")
        String holderName
) {
}
