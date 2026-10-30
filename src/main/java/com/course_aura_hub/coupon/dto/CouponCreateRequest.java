package com.course_aura_hub.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponCreateRequest(
        @NotBlank(message = "Coupon code is required")
        @Pattern(regexp = "[A-Za-z0-9_-]{3,40}", message = "Code must be 3 to 40 letters, digits, hyphens or underscores")
        String code,
        @NotBlank(message = "Discount type is required")
        @Pattern(regexp = "(?i)PERCENT|FIXED", message = "Discount type must be PERCENT or FIXED")
        String discountType,
        @NotNull(message = "Discount value is required")
        @DecimalMin(value = "0.01", message = "Discount must be greater than zero")
        BigDecimal discountValue,
        // Limit to one course; omit for every course
        String courseSlug,
        @Min(value = 1, message = "Max redemptions must be at least 1")
        Integer maxRedemptions,
        Instant startsAt,
        Instant expiresAt
) {
}
