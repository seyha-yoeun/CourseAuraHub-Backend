package com.course_aura_hub.quiz.dto;

import java.time.Instant;
import java.util.List;

/** A quiz as a learner sees it. It never says which option is correct. */
public record QuizResponse(
        Integer lessonId,
        int passPercent,
        List<Question> questions,
        long attempts,
        // Null until the first attempt
        Integer bestScore,
        boolean passed,
        QuizRules rules,
        // Null when attempts are unlimited
        Integer attemptsRemaining,
        // Set while a retry cooldown is running
        Instant nextAttemptAt,
        // A timed, pooled or shuffled quiz shows its questions only after POST .../quiz/start
        boolean startRequired,
        // Deadline of the attempt in progress; null when untimed or not started
        Instant expiresAt
) {

    public record Question(Integer id, String prompt, List<Option> options) {
    }

    public record Option(Integer id, String text) {
    }
}
