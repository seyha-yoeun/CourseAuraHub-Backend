package com.course_aura_hub.coupon.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponResponse(
        Integer id,
        String code,
        String discountType,
        BigDecimal discountValue,
        String courseSlug,
        Integer maxRedemptions,
        long paidRedemptions,
        Instant startsAt,
        Instant expiresAt,
        boolean active,
        Instant createdAt
) {
}
