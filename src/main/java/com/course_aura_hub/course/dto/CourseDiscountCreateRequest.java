package com.course_aura_hub.course.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

public record CourseDiscountCreateRequest(
        @NotBlank(message = "Course is required")
        String courseSlug,
        @NotBlank(message = "Discount type is required")
        @Pattern(regexp = "(?i)PERCENT|FIXED", message = "Discount type must be PERCENT or FIXED")
        String discountType,
        @NotNull(message = "Discount value is required")
        @DecimalMin(value = "0.01", message = "Discount must be greater than zero")
        BigDecimal discountValue,
        // Omit to start now
        Instant startsAt,
        // Omit to run until an admin ends it
        Instant endsAt
) {
}
