package com.course_aura_hub.quiz.dto;

import com.course_aura_hub.curriculum.model.Lesson;

/**
 * How a quiz may be taken. Null fields are off.
 *
 * @param maxAttempts          attempts per learner, before any granted by staff
 * @param retryCooldownMinutes wait after a failed attempt
 * @param timeLimitMinutes     time to submit after starting
 * @param questionsPerAttempt  questions drawn at random from the pool for each attempt
 * @param shuffle              whether question and option order is shuffled per attempt
 */
public record QuizRules(
        Integer maxAttempts,
        Integer retryCooldownMinutes,
        Integer timeLimitMinutes,
        Integer questionsPerAttempt,
        boolean shuffle
) {

    public static QuizRules of(Lesson lesson) {
        return new QuizRules(
                lesson.getQuizMaxAttempts(),
                lesson.getQuizRetryCooldownMinutes(),
                lesson.getQuizTimeLimitMinutes(),
                lesson.getQuizQuestionsPerAttempt(),
                lesson.isQuizShuffle()
        );
    }
}
