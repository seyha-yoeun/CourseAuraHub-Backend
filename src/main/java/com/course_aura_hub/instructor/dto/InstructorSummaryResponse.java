package com.course_aura_hub.instructor.dto;

import java.math.BigDecimal;

public record InstructorSummaryResponse(
        int courses,
        int publishedCourses,
        int awaitingReview,
        int students,
        BigDecimal revenue,
        BigDecimal averageRating
) {
}
