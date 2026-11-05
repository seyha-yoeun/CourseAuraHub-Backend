package com.course_aura_hub.quiz.dto;

/** A learner's attempts on one quiz after a grant. {@code attemptsRemaining} is null when unlimited. */
public record QuizAttemptGrantResponse(
        Integer studentId,
        Integer lessonId,
        int extraAttempts,
        long attemptsUsed,
        Integer attemptsRemaining
) {
}
