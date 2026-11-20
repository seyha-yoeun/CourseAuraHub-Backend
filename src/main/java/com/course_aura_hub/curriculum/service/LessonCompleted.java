package com.course_aura_hub.curriculum.service;

/**
 * A learner completed a lesson, inside the completing transaction, with their progress in the course afterwards.
 */
public record LessonCompleted(Integer studentId, Integer courseId, long completedLessons, long totalLessons) {
}
