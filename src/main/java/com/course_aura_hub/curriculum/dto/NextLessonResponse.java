package com.course_aura_hub.curriculum.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The first lesson, in curriculum order, the learner has not completed yet")
public record NextLessonResponse(
        Integer lessonId,
        String title,
        String type,
        Integer durationMinutes,
        Integer sectionId,
        String sectionTitle
) {
}
