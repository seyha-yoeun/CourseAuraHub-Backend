package com.course_aura_hub.instructor.dto;

import com.course_aura_hub.course.dto.CourseResponse;

public record InstructorCourseResponse(
        CourseResponse course,
        // Why an admin sent the course back
        String reviewNote
) {
}
