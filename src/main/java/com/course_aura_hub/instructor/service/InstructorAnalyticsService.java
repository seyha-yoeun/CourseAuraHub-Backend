package com.course_aura_hub.instructor.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse;

public interface InstructorAnalyticsService {

    /** Funnel, quiz, assignment, review and monthly figures for a course the actor manages. */
    CourseAnalyticsResponse courseAnalytics(Actor actor, String slug);
}
