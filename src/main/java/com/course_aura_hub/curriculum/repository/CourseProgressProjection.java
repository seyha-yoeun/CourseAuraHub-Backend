package com.course_aura_hub.curriculum.repository;

import java.time.Instant;

public interface CourseProgressProjection {

    Integer getCourseId();

    Long getCompletedLessons();

    Instant getLastCompletedAt();
}
