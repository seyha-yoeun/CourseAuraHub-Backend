package com.course_aura_hub.instructor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseReviewRejectRequest(
        @NotBlank(message = "Tell the instructor what to change")
        @Size(max = 1000, message = "Keep the reason under 1000 characters")
        String note
) {
}
