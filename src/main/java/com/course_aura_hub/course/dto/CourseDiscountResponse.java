package com.course_aura_hub.course.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CourseDiscountResponse(
        Integer id,
        String courseSlug,
        String courseTitle,
        String discountType,
        BigDecimal discountValue,
        BigDecimal regularPrice,
        BigDecimal salePrice,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        // Whether learners are getting this price right now
        boolean running,
        Instant createdAt
) {
}
