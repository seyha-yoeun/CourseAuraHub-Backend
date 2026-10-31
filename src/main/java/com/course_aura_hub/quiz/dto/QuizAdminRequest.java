package com.course_aura_hub.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuizAdminRequest(

        @NotNull(message = "Pass mark is required")
        @Min(value = 1, message = "Pass mark must be at least 1%")
        @Max(value = 100, message = "Pass mark must not exceed 100%")
        Integer passPercent,

        @NotEmpty(message = "Add at least one question")
        @Valid
        List<Question> questions
) {

    public record Question(
            @NotBlank(message = "Question text is required")
            @Size(max = 1000, message = "Question text must not exceed 1000 characters")
            String prompt,

            @Size(max = 2000, message = "Explanation must not exceed 2000 characters")
            String explanation,

            @NotNull(message = "Each question needs 2 to 8 options")
            @Size(min = 2, max = 8, message = "Each question needs 2 to 8 options")
            @Valid
            List<Option> options
    ) {
    }

    public record Option(
            @NotBlank(message = "Option text is required")
            @Size(max = 500, message = "Option text must not exceed 500 characters")
            String text,

            boolean correct
    ) {
    }
}
