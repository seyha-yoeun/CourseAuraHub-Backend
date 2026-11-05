package com.course_aura_hub.instructor.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Null fields keep their value; blank ones clear it. Bio, avatar, website and Twitter are edited on /me/profile. */
public record InstructorProfileRequest(
        @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{1,38})[a-z0-9]$",
                message = "Handle must be 3 to 40 lowercase letters, digits or dashes, not starting or ending with a dash")
        String handle,

        @Size(max = 160, message = "Headline must not exceed 160 characters")
        String headline,

        @Size(max = 300, message = "Link must not exceed 300 characters")
        @Pattern(regexp = "^$|^https://([a-z0-9-]+\\.)*linkedin\\.com/\\S*$", message = "LinkedIn link must be an https linkedin.com URL")
        String linkedinUrl,

        @Size(max = 300, message = "Link must not exceed 300 characters")
        @Pattern(regexp = "^$|^https://([a-z0-9-]+\\.)*github\\.com/\\S*$", message = "GitHub link must be an https github.com URL")
        String githubUrl,

        @Size(max = 300, message = "Link must not exceed 300 characters")
        @Pattern(regexp = "^$|^https://([a-z0-9-]+\\.)*(youtube\\.com|youtu\\.be)/\\S*$", message = "YouTube link must be an https youtube.com URL")
        String youtubeUrl
) {
}
