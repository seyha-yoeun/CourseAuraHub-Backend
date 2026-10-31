package com.course_aura_hub.cart.dto;

import java.math.BigDecimal;

public record CartCouponPreviewResponse(
        String code,
        // A code is used once per learner, so it applies to one course in the cart
        String courseSlug,
        String courseTitle,
        BigDecimal couponDiscount,
        BigDecimal total
) {
}
