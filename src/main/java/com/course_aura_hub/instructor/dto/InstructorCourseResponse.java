package com.course_aura_hub.instructor.dto;

import com.course_aura_hub.revision.dto.RevisionDtos;

import com.course_aura_hub.course.dto.CourseResponse;

public record InstructorCourseResponse(
        CourseResponse course,
        // Why an admin sent the course back
        String reviewNote,
        // Changes to the live course waiting for an admin; null when there are none
        RevisionDtos.RevisionResponse pendingRevision
) {
}
