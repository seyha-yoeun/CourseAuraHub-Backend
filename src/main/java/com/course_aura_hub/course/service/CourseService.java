package com.course_aura_hub.course.service;

import com.course_aura_hub.course.dto.CourseCreateRequest;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.dto.CourseSearchCriteria;
import com.course_aura_hub.course.dto.CourseUpdateRequest;

import java.util.List;

public interface CourseService {

    List<CourseResponse> findAll(CourseSearchCriteria criteria, boolean includeUnpublished);

    CourseResponse findBySlug(String slug, boolean includeUnpublished);

    CourseResponse create(CourseCreateRequest request);

    CourseResponse update(String slug, CourseUpdateRequest request);

    void delete(String slug);
}
