package com.course_aura_hub.coupon.dto;

import java.math.BigDecimal;

public record CouponPreviewResponse(
        String code,
        BigDecimal listAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount
) {
}
