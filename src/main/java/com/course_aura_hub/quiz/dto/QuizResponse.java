package com.course_aura_hub.quiz.dto;

import java.util.List;

/** A quiz as a learner sees it. It never says which option is correct. */
public record QuizResponse(
        Integer lessonId,
        int passPercent,
        List<Question> questions,
        long attempts,
        // Null until the first attempt
        Integer bestScore,
        boolean passed
) {

    public record Question(Integer id, String prompt, List<Option> options) {
    }

    public record Option(Integer id, String text) {
    }
}
