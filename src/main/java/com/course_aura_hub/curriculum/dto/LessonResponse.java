package com.course_aura_hub.curriculum.dto;

public record LessonResponse(
        Integer id,
        String title,
        String type,
        Integer durationMinutes,
        boolean preview,
        Integer position,
        boolean locked,
        String contentUrl,
        boolean completed,
        // Where the viewer stopped in this video; null if nothing is saved
        Integer resumePositionSeconds
) {
}
