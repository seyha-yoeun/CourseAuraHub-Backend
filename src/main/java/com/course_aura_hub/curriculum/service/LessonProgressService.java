package com.course_aura_hub.curriculum.service;

import com.course_aura_hub.curriculum.dto.LessonPositionResponse;
import com.course_aura_hub.curriculum.dto.LessonProgressResponse;
import com.course_aura_hub.curriculum.dto.NextLessonResponse;
import com.course_aura_hub.curriculum.repository.CourseProgressProjection;

import java.util.Collection;
import java.util.Map;

public interface LessonProgressService {

    LessonProgressResponse markComplete(String email, Integer lessonId);

    LessonProgressResponse markIncomplete(String email, Integer lessonId);

    /** Remembers where the learner stopped in a video. */
    LessonPositionResponse savePosition(String email, Integer lessonId, int positionSeconds);

    /**
     * Completed-lesson counts and last activity per course for a learner, keyed by course id.
     */
    Map<Integer, CourseProgressProjection> summarizeByStudent(Integer studentId);

    /**
     * The first not-yet-completed lesson of each course, keyed by course id. Courses the learner has finished
     * (or that have no lessons) are absent from the map.
     */
    Map<Integer, NextLessonResponse> findNextLessons(Integer studentId, Collection<Integer> courseIds);
}
