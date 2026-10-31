package com.course_aura_hub.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Integer courseId,
        String slug,
        String title,
        String thumbnail,
        String instructor,
        // Regular price, before any running sale
        BigDecimal listPrice,
        // What the course costs today
        BigDecimal price
) {
}
