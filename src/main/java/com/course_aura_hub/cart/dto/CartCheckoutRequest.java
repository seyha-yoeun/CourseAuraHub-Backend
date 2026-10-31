package com.course_aura_hub.cart.dto;

public record CartCheckoutRequest(
        // Optional discount code
        String couponCode
) {
}
