package com.course_aura_hub.instructor.dto;

import com.course_aura_hub.course.dto.CourseResponse;

import java.math.BigDecimal;
import java.util.List;

/** What anyone can see about an instructor. Never includes the email address. */
public record PublicInstructorResponse(
        String handle,
        String name,
        String headline,
        String bio,
        String avatarUrl,
        Links links,
        Stats stats,
        List<CourseResponse> courses
) {

    public record Links(String website, String twitter, String linkedin, String github, String youtube) {
    }

    public record Stats(int courses, long students, BigDecimal averageRating, long reviews) {
    }
}
