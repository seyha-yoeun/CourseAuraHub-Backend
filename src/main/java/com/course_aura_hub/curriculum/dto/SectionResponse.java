package com.course_aura_hub.curriculum.dto;

import java.util.List;

public record SectionResponse(
        Integer id,
        String title,
        Integer position,
        int totalDurationMinutes,
        List<LessonResponse> lessons
) {
}
