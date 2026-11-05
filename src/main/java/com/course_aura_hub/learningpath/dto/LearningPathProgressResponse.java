package com.course_aura_hub.learningpath.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A learner's progress through a path.
 *
 * @param nextCourseSlug the first course not completed yet; null once the path is complete
 * @param certificate    issued when every course is complete; null before
 */
public record LearningPathProgressResponse(
        String slug,
        String title,
        int totalCourses,
        int enrolledCourses,
        int completedCourses,
        BigDecimal percent,
        boolean completed,
        String nextCourseSlug,
        Certificate certificate,
        List<Step> courses
) {

    public record Step(String slug, String title, String thumbnail, boolean enrolled, boolean completed) {
    }

    public record Certificate(String certificateNumber, Instant issuedAt) {
    }
}
