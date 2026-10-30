package com.course_aura_hub.coupon.dto;

import jakarta.validation.constraints.Min;

import java.time.Instant;

/**
 * Partial update: null fields are left unchanged. The discount itself is fixed once a code exists.
 */
public record CouponUpdateRequest(
        Boolean active,
        @Min(value = 1, message = "Max redemptions must be at least 1")
        Integer maxRedemptions,
        Instant expiresAt
) {
}
