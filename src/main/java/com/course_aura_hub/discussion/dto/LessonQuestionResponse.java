package com.course_aura_hub.discussion.dto;

import java.time.Instant;
import java.util.List;

public record LessonQuestionResponse(
        Integer id,
        Integer lessonId,
        String authorName,
        String body,
        boolean resolved,
        Instant createdAt,
        // Whether the caller may resolve or delete it
        boolean canManage,
        List<LessonAnswerResponse> answers
) {
}
