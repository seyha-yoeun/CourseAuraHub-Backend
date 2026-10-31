package com.course_aura_hub.cart.dto;

import jakarta.validation.constraints.NotBlank;

public record CartCouponRequest(@NotBlank(message = "Coupon code is required") String code) {
}
