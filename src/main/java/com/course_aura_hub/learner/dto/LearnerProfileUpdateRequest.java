package com.course_aura_hub.learner.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update: null fields are left unchanged, blank optional fields are cleared.
 */
public record LearnerProfileUpdateRequest(

        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @Size(max = 500, message = "Image URL must not exceed 500 characters")
        String imageUrl,

        @Size(max = 1000, message = "Bio must not exceed 1000 characters")
        String bio,

        @Size(max = 500, message = "Website must not exceed 500 characters")
        String website,

        @Size(max = 100, message = "Twitter handle must not exceed 100 characters")
        String twitter
) {
}
