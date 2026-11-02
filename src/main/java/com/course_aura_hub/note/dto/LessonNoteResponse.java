package com.course_aura_hub.note.dto;

import java.time.Instant;

public record LessonNoteResponse(
        Integer id,
        Integer lessonId,
        String body,
        Integer positionSeconds,
        Instant createdAt,
        Instant updatedAt
) {
}
