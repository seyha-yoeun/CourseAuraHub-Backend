package com.course_aura_hub.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        String orderNumber,
        String courseSlug,
        String courseTitle,
        String thumbnail,
        BigDecimal amount,
        String currency,
        String status,
        // Where to pay; null once the order is no longer pending
        String checkoutUrl,
        Instant createdAt,
        Instant paidAt,
        Instant refundedAt
) {
}
