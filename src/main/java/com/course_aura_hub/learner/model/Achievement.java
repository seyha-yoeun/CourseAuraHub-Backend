package com.course_aura_hub.learner.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Badges a learner earns from their learning record. They are derived on read, never stored.
 */
@Getter
@RequiredArgsConstructor
public enum Achievement {

    FIRST_STEPS("First Steps", "Complete your first lesson", Metric.LESSONS_COMPLETED, 1),
    KNOWLEDGE_SEEKER("Knowledge Seeker", "Complete 10 lessons", Metric.LESSONS_COMPLETED, 10),
    LESSON_MARATHON("Lesson Marathon", "Complete 50 lessons", Metric.LESSONS_COMPLETED, 50),
    COURSE_FINISHER("Course Finisher", "Earn your first course certificate", Metric.CERTIFICATES_EARNED, 1),
    LIFELONG_LEARNER("Lifelong Learner", "Earn 5 course certificates", Metric.CERTIFICATES_EARNED, 5),
    ON_A_ROLL("On a Roll", "Learn 3 days in a row", Metric.LONGEST_STREAK_DAYS, 3),
    WEEK_WARRIOR("Week Warrior", "Learn 7 days in a row", Metric.LONGEST_STREAK_DAYS, 7),
    UNSTOPPABLE("Unstoppable", "Learn 30 days in a row", Metric.LONGEST_STREAK_DAYS, 30),
    FIRST_REVIEW("Voice of the Class", "Review a course you're taking", Metric.REVIEWS_WRITTEN, 1);

    public enum Metric {
        LESSONS_COMPLETED,
        CERTIFICATES_EARNED,
        LONGEST_STREAK_DAYS,
        REVIEWS_WRITTEN
    }

    private final String title;
    private final String description;
    private final Metric metric;
    private final long target;
}
