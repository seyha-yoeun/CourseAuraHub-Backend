package com.course_aura_hub.discussion.dto;

import java.time.Instant;

/** One line of the staff inbox. */
public record OpenQuestionResponse(
        Integer id,
        Integer lessonId,
        String lessonTitle,
        String courseSlug,
        String courseTitle,
        String authorName,
        String body,
        int answerCount,
        Instant createdAt
) {
}
