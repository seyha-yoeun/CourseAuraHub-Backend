package com.course_aura_hub.review.dto;

import java.time.Instant;

public record ReviewResponse(
        Integer id,
        Integer rating,
        String comment,
        String reviewerName,
        String reviewerAvatar,
        Instant createdAt,
        Instant updatedAt
) {
}
