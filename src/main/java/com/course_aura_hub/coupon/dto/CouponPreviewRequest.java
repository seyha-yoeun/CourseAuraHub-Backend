package com.course_aura_hub.coupon.dto;

import jakarta.validation.constraints.NotBlank;

public record CouponPreviewRequest(
        @NotBlank(message = "Course slug is required")
        String courseSlug,
        @NotBlank(message = "Coupon code is required")
        String code
) {
}
