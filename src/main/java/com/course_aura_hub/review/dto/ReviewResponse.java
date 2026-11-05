package com.course_aura_hub.review.dto;

import java.time.Instant;

public record ReviewResponse(
        Integer id,
        Integer rating,
        String comment,
        String reviewerName,
        String reviewerAvatar,
        Instant createdAt,
        Instant updatedAt,
        int helpfulCount,
        // The course staff's public reply, or null
        Reply reply
) {

    public record Reply(String body, String authorName, Instant repliedAt) {
    }
}
