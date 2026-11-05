package com.course_aura_hub.instructor.dto;

/** A short instructor reference for lists and course pages. */
public record InstructorCardResponse(
        String handle,
        String name,
        String headline,
        String avatarUrl,
        int courses,
        long students
) {
}
