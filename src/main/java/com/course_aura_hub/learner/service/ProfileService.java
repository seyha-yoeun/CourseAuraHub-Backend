package com.course_aura_hub.learner.service;

import com.course_aura_hub.learner.dto.LearnerProfileResponse;
import com.course_aura_hub.learner.dto.LearnerProfileUpdateRequest;

public interface ProfileService {

    LearnerProfileResponse getProfile(String email);

    LearnerProfileResponse updateProfile(String email, LearnerProfileUpdateRequest request);
}
