package com.course_aura_hub.cart.service;

import com.course_aura_hub.cart.dto.CartCouponPreviewResponse;
import com.course_aura_hub.cart.dto.CartResponse;

public interface CartService {

    CartResponse findMine(String email);

    /** Safe to repeat: a course already in the cart stays there once. */
    CartResponse add(String email, String courseSlug);

    CartResponse remove(String email, String courseSlug);

    CartCouponPreviewResponse previewCoupon(String email, String code);
}
