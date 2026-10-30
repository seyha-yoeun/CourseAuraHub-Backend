package com.course_aura_hub.order.facade;

import com.course_aura_hub.order.dto.OrderResponse;

public interface CheckoutFacade {

    OrderResponse checkout(String email, String courseSlug);

    void handleWebhook(String provider, String signature, String payload);

    OrderResponse refund(String orderNumber);
}
