package com.course_aura_hub.curriculum.dto;

import java.util.List;

public record CurriculumResponse(
        Integer courseId,
        String courseSlug,
        boolean enrolled,
        int totalLessons,
        int totalDurationMinutes,
        CourseProgress progress,
        // First lesson the enrolled viewer has not completed; null when not enrolled or the course is completed
        Integer nextLessonId,
        List<SectionResponse> sections
) {
}
