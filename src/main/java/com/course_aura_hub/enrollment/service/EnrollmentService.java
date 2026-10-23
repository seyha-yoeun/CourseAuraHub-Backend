package com.course_aura_hub.enrollment.service;

import com.course_aura_hub.enrollment.dto.EnrollmentCreateRequest;
import com.course_aura_hub.enrollment.dto.EnrollmentResponse;

import java.util.List;

public interface EnrollmentService {

    List<EnrollmentResponse> findRecent(int limit);

    EnrollmentResponse create(EnrollmentCreateRequest request);

    void delete(Integer id);
}
