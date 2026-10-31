package com.course_aura_hub.order.facade;

import com.course_aura_hub.cart.dto.CheckoutResponse;
import com.course_aura_hub.order.dto.OrderResponse;

public interface CheckoutFacade {

    OrderResponse checkout(String email, String courseSlug, String couponCode);

    CheckoutResponse checkoutCart(String email, String couponCode);

    void handleWebhook(String provider, String signature, String payload);

    OrderResponse refund(String orderNumber);
}
