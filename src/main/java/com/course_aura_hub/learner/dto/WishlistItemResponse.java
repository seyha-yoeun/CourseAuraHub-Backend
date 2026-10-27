package com.course_aura_hub.learner.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WishlistItemResponse(
        Integer courseId,
        String slug,
        String title,
        String thumbnail,
        String instructor,
        String level,
        BigDecimal price,
        BigDecimal originalPrice,
        BigDecimal rating,
        Instant addedAt
) {
}
