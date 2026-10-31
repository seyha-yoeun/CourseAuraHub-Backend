package com.course_aura_hub.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        BigDecimal listTotal,
        BigDecimal total,
        String currency
) {
}
