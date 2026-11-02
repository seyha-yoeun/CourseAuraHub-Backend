package com.course_aura_hub.instructor.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.dto.CourseCreateRequest;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.dto.CourseUpdateRequest;
import com.course_aura_hub.instructor.dto.InstructorCourseResponse;
import com.course_aura_hub.instructor.dto.InstructorStudentResponse;
import com.course_aura_hub.instructor.dto.InstructorSummaryResponse;

import java.util.List;

public interface InstructorService {

    InstructorSummaryResponse summary(Actor actor);

    List<InstructorCourseResponse> findMine(Actor actor);

    InstructorCourseResponse find(Actor actor, String slug);

    /** Always starts as a draft owned by the caller. */
    InstructorCourseResponse create(Actor actor, CourseCreateRequest request);

    /** Can't change the status: only an admin's approval publishes a course. */
    InstructorCourseResponse update(Actor actor, String slug, CourseUpdateRequest request);

    /** Only for a draft nobody has joined. */
    void delete(Actor actor, String slug);

    InstructorCourseResponse submitForReview(Actor actor, String slug);

    List<InstructorStudentResponse> findStudents(Actor actor, String slug);

    List<CourseResponse> findAwaitingReview();

    CourseResponse approve(String slug);

    CourseResponse reject(String slug, String note);
}
