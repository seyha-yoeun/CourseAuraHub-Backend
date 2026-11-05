package com.course_aura_hub.instructor.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.instructor.dto.InstructorCardResponse;
import com.course_aura_hub.instructor.dto.InstructorProfileRequest;
import com.course_aura_hub.instructor.dto.InstructorProfileResponse;
import com.course_aura_hub.instructor.dto.PublicInstructorResponse;

import java.util.List;

public interface InstructorProfileService {

    /** The caller's profile, created with a handle from their name the first time. */
    InstructorProfileResponse findMine(Actor actor);

    InstructorProfileResponse updateMine(Actor actor, InstructorProfileRequest request);

    /** Public. Only for accounts that can still teach. */
    PublicInstructorResponse findByHandle(String handle);

    /** Public. Instructors with at least one published course, most learners first. */
    List<InstructorCardResponse> findListed(Integer limit);

    /** Public. The owner of a published course, if they have a profile. */
    InstructorCardResponse findForCourse(String courseSlug);
}
