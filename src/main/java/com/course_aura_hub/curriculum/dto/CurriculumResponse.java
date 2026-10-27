package com.course_aura_hub.curriculum.dto;

import java.util.List;

public record CurriculumResponse(
        Integer courseId,
        String courseSlug,
        boolean enrolled,
        int totalLessons,
        int totalDurationMinutes,
        CourseProgress progress,
        List<SectionResponse> sections
) {
}
