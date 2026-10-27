package com.course_aura_hub.learner.dto;

import jakarta.validation.constraints.NotBlank;

public record SelfEnrollRequest(

        @NotBlank(message = "Course slug is required")
        String courseSlug
) {
}
