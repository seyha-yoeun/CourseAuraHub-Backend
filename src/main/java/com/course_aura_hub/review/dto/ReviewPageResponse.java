package com.course_aura_hub.review.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReviewPageResponse(
        List<ReviewResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal averageRating
) {
}
