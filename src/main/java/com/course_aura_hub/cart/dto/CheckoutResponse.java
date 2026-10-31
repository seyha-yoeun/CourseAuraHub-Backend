package com.course_aura_hub.cart.dto;

import com.course_aura_hub.order.dto.OrderResponse;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutResponse(
        String groupNumber,
        BigDecimal amount,
        String currency,
        // Where to pay; null when nothing is left to pay
        String checkoutUrl,
        List<OrderResponse> orders
) {
}
