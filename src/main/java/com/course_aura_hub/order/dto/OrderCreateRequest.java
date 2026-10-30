package com.course_aura_hub.order.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderCreateRequest(
        @NotBlank(message = "Course slug is required")
        String courseSlug,
        // Optional discount code
        String couponCode
) {
}
