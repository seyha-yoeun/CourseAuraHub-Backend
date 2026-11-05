package com.course_aura_hub.instructor.dto;

/** The caller's own profile settings. */
public record InstructorProfileResponse(
        String handle,
        String headline,
        String linkedinUrl,
        String githubUrl,
        String youtubeUrl,
        String publicPath
) {
}
