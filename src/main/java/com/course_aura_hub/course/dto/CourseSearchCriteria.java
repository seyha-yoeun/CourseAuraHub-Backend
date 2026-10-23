package com.course_aura_hub.course.dto;

public record CourseSearchCriteria(
        String status,
        String category,
        String search,
        String sort,
        String order
) {
}
