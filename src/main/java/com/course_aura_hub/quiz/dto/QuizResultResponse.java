package com.course_aura_hub.quiz.dto;

import com.course_aura_hub.curriculum.dto.LessonProgressResponse;

import java.util.List;

public record QuizResultResponse(
        int scorePercent,
        int correctCount,
        int totalCount,
        int passPercent,
        boolean passed,
        List<QuestionResult> questions,
        // Null unless this attempt passed, because only passing changes progress
        LessonProgressResponse progress
) {

    /**
     * @param correctOptionId null until the quiz is passed, so retakes stay meaningful
     * @param explanation     null until the quiz is passed
     */
    public record QuestionResult(Integer questionId, boolean correct, Integer correctOptionId, String explanation) {
    }
}
