package com.course_aura_hub.curriculum.dto;

public record LessonProgressResponse(
        Integer lessonId,
        String courseSlug,
        boolean lessonCompleted,
        CourseProgress courseProgress
) {
}
