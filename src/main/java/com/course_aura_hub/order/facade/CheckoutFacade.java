package com.course_aura_hub.order.facade;

import com.course_aura_hub.cart.dto.CheckoutResponse;
import com.course_aura_hub.order.dto.OrderResponse;

public interface CheckoutFacade {

    OrderResponse checkout(String email, String courseSlug, String couponCode, String paymentMethod);

    /** Buys a course for someone else; once paid, a gift code is emailed to them. */
    OrderResponse checkoutGift(String email, String courseSlug, String paymentMethod, String recipientEmail, String message);

    CheckoutResponse checkoutCart(String email, String couponCode, String paymentMethod);

    void handleWebhook(String provider, String signature, String payload);

    OrderResponse refund(String orderNumber);
}
