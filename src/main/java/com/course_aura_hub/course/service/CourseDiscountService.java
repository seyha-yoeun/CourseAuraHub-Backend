package com.course_aura_hub.course.service;

import com.course_aura_hub.course.dto.CourseDiscountCreateRequest;
import com.course_aura_hub.course.dto.CourseDiscountResponse;
import com.course_aura_hub.course.dto.CourseDiscountUpdateRequest;

import java.util.List;

public interface CourseDiscountService {

    List<CourseDiscountResponse> findAll(String courseSlug);

    CourseDiscountResponse create(CourseDiscountCreateRequest request);

    CourseDiscountResponse update(Integer id, CourseDiscountUpdateRequest request);

    void delete(Integer id);
}
