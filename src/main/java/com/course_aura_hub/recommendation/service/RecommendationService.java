package com.course_aura_hub.recommendation.service;

import com.course_aura_hub.course.dto.CourseResponse;

import java.util.List;

public interface RecommendationService {

    int DEFAULT_LIMIT = 6;
    int MAX_LIMIT = 20;

    /** Published courses similar to the given published course, best match first. */
    List<CourseResponse> related(String slug, Integer limit);

    /** Published courses for the learner, excluding ones they are enrolled in or have wishlisted. */
    List<CourseResponse> forLearner(String email, Integer limit);
}
