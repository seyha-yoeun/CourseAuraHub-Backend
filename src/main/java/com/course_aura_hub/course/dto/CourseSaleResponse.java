package com.course_aura_hub.course.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CourseSaleResponse(
        BigDecimal salePrice,
        String discountType,
        BigDecimal discountValue,
        // Null when the sale has no end date
        Instant endsAt
) {
}
