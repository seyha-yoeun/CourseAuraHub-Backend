package com.course_aura_hub.learner.dto;

import com.course_aura_hub.curriculum.dto.CourseProgress;

import java.time.Instant;
import java.time.LocalDate;

public record MyCourseResponse(
        Integer courseId,
        String slug,
        String title,
        String thumbnail,
        String instructor,
        String level,
        LocalDate enrolledAt,
        CourseProgress progress,
        Instant lastActivityAt
) {
}
