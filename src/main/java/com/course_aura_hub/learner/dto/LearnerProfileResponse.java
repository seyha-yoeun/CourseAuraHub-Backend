package com.course_aura_hub.learner.dto;

import java.util.UUID;

public record LearnerProfileResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String imageUrl,
        String bio,
        String website,
        String twitter,
        String provider,
        boolean hasPassword
) {
}
