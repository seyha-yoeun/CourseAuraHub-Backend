package com.course_aura_hub.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Partial update: null fields are left unchanged. Orders keep the price they were placed at.
 */
public record CourseDiscountUpdateRequest(
        @Pattern(regexp = "(?i)PERCENT|FIXED", message = "Discount type must be PERCENT or FIXED")
        String discountType,
        @DecimalMin(value = "0.01", message = "Discount must be greater than zero")
        BigDecimal discountValue,
        Instant startsAt,
        Instant endsAt,
        Boolean active
) {
}
