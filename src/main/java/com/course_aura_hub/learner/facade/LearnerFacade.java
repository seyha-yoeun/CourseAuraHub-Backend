package com.course_aura_hub.learner.facade;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.learner.dto.ChangePasswordRequest;
import com.course_aura_hub.learner.dto.MyCourseResponse;

import java.util.List;

/**
 * Orchestrates learner flows that span the learner, enrollment, curriculum, and auth modules.
 */
public interface LearnerFacade {

    List<MyCourseResponse> findMyCourses(String email);

    MyCourseResponse enroll(String email, String courseSlug);

    AuthResponse changePassword(String email, ChangePasswordRequest request);
}
