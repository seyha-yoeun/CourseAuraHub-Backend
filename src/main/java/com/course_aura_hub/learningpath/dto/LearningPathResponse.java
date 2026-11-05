package com.course_aura_hub.learningpath.dto;

import com.course_aura_hub.course.dto.CourseResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @param price what the path's courses cost together right now, after running sales
 */
public record LearningPathResponse(
        Integer id,
        String slug,
        String title,
        String description,
        String thumbnail,
        String status,
        int courseCount,
        int totalLessons,
        BigDecimal totalHours,
        BigDecimal price,
        List<CourseResponse> courses,
        Instant updatedAt
) {
}
