package com.course_aura_hub.course.service;

import com.course_aura_hub.course.dto.CourseCreateRequest;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.dto.CourseSearchCriteria;
import com.course_aura_hub.course.dto.CourseUpdateRequest;

import java.util.List;

public interface CourseService {

    List<CourseResponse> findAll(CourseSearchCriteria criteria, boolean includeUnpublished);

    /**
     * @param admin       admins see unpublished courses and the enrolled-student list
     * @param viewerEmail the caller, used to report whether they are enrolled
     */
    CourseResponse findBySlug(String slug, boolean admin, String viewerEmail);

    CourseResponse create(CourseCreateRequest request);

    CourseResponse update(String slug, CourseUpdateRequest request);

    void delete(String slug);
}
