package com.course_aura_hub.curriculum.service;

import com.course_aura_hub.curriculum.dto.LessonProgressResponse;
import com.course_aura_hub.curriculum.repository.CourseProgressProjection;

import java.util.Map;

public interface LessonProgressService {

    LessonProgressResponse markComplete(String email, Integer lessonId);

    LessonProgressResponse markIncomplete(String email, Integer lessonId);

    /**
     * Completed-lesson counts and last activity per course for a learner, keyed by course id.
     */
    Map<Integer, CourseProgressProjection> summarizeByStudent(Integer studentId);
}
