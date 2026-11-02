package com.course_aura_hub.assignment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignmentRequest(

        @NotBlank(message = "Instructions are required")
        @Size(max = 5000, message = "Instructions must not exceed 5000 characters")
        String instructions,

        @NotNull(message = "Pass score is required")
        @Min(value = 0, message = "Pass score must be between 0 and 100")
        @Max(value = 100, message = "Pass score must be between 0 and 100")
        Integer passScore,

        boolean required
) {
}
