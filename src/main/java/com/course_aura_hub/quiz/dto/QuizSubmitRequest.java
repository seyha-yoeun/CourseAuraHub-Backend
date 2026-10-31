package com.course_aura_hub.quiz.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record QuizSubmitRequest(
        // Chosen option id by question id
        @NotEmpty(message = "Answer the questions first")
        Map<Integer, Integer> answers
) {
}
