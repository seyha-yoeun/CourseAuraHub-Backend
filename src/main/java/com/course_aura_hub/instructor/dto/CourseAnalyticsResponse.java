package com.course_aura_hub.instructor.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Aggregates for one course. Never contains learner identities, so instructors can see it. */
public record CourseAnalyticsResponse(
        String slug,
        String title,
        Learners learners,
        Reviews reviews,
        Assignments assignments,
        List<Month> monthly,
        List<LessonFunnel> lessons
) {

    public record Learners(long enrolled, long completed, BigDecimal completionRate) {
    }

    /** Distribution keys are the star ratings 1 to 5. */
    public record Reviews(long count, BigDecimal averageRating, Map<Integer, Long> distribution) {
    }

    public record Assignments(long pendingReview, long graded, BigDecimal averageScore) {
    }

    public record Month(int month, int enrollments, BigDecimal revenue) {
    }

    /**
     * One lesson in curriculum order. {@code dropOff} is how many percentage points fewer learners completed this
     * lesson than the one before it; the biggest value is where learners give up.
     */
    public record LessonFunnel(
            Integer lessonId,
            String section,
            String title,
            String type,
            long completedLearners,
            BigDecimal completionRate,
            BigDecimal dropOff,
            Video video,
            Quiz quiz
    ) {
    }

    /** Learners who started the video and how far in they are on average. */
    public record Video(long learnersWatching, BigDecimal averageWatchSeconds) {
    }

    public record Quiz(long attempts, long learners, BigDecimal passRate, BigDecimal averageScore,
                       BigDecimal averageAttemptsPerLearner) {
    }
}
