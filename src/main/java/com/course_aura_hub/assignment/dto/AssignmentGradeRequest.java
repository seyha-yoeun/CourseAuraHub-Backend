package com.course_aura_hub.assignment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignmentGradeRequest(

        @NotNull(message = "Score is required")
        @Min(value = 0, message = "Score must be between 0 and 100")
        @Max(value = 100, message = "Score must be between 0 and 100")
        Integer score,

        @Size(max = 2000, message = "Keep feedback under 2000 characters")
        String feedback
) {
}
