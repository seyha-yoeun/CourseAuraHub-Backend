package com.course_aura_hub.learner.service;

import com.course_aura_hub.learner.dto.LearningStreakResponse;
import com.course_aura_hub.learner.model.Achievement;

import java.time.LocalDate;
import java.util.Map;

/**
 * A learner's lifetime learning record, with days resolved in one time zone.
 *
 * @param lessonsByDay lessons completed per calendar day (days without activity are absent)
 */
public record LearnerStats(
        long lessonsCompleted,
        long minutesLearned,
        long certificatesEarned,
        long reviewsWritten,
        LearningStreakResponse streak,
        Map<LocalDate, Long> lessonsByDay
) {

    public static final LearnerStats EMPTY = new LearnerStats(0, 0, 0, 0, new LearningStreakResponse(0, 0, false), Map.of());

    public long value(Achievement.Metric metric) {
        return switch (metric) {
            case LESSONS_COMPLETED -> lessonsCompleted;
            case CERTIFICATES_EARNED -> certificatesEarned;
            case LONGEST_STREAK_DAYS -> streak.longestDays();
            case REVIEWS_WRITTEN -> reviewsWritten;
        };
    }
}
