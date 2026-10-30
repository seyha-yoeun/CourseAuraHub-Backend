package com.course_aura_hub.order.service;

import com.course_aura_hub.order.dto.AdminOrderPageResponse;
import com.course_aura_hub.order.dto.OrderResponse;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.payment.PaymentEvent;
import com.course_aura_hub.order.payment.PaymentGateway;
import com.course_aura_hub.order.payment.PaymentSession;

import java.util.List;

public interface OrderService {

    /** Reuses the learner's open order for the course when its price still matches, else opens a new one. */
    Order openOrder(String email, String courseSlug, String currency, String provider);

    OrderResponse attachSession(String orderNumber, PaymentSession session);

    void markFailed(String orderNumber);

    List<OrderResponse> findMine(String email);

    OrderResponse findMine(String email, String orderNumber);

    OrderResponse cancelMine(String email, String orderNumber);

    /** Applies a verified provider event. Safe to call repeatedly: providers redeliver webhooks. */
    void applyPaymentEvent(PaymentEvent event);

    AdminOrderPageResponse findAll(String status, int page, int size);

    OrderResponse refund(String orderNumber, PaymentGateway gateway);
}
