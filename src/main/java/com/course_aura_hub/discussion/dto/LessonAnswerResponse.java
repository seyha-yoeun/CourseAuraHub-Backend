package com.course_aura_hub.discussion.dto;

import java.time.Instant;

public record LessonAnswerResponse(
        Integer id,
        String authorName,
        boolean fromStaff,
        String body,
        Instant createdAt,
        // Whether the caller may delete it
        boolean canDelete
) {
}
