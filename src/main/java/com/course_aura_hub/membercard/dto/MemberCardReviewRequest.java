package com.course_aura_hub.membercard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberCardReviewRequest(
        @NotBlank(message = "Give the learner a reason")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String note
) {
}
