package com.course_aura_hub.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuizAttemptGrantRequest(
        @NotNull(message = "Student is required")
        Integer studentId,
        @NotNull(message = "Number of attempts is required")
        @Min(value = 1, message = "Grant at least 1 attempt")
        @Max(value = 20, message = "Grant at most 20 attempts at a time")
        Integer extraAttempts
) {
}
