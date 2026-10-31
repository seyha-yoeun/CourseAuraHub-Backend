package com.course_aura_hub.order.service;

import com.course_aura_hub.order.dto.AdminOrderPageResponse;
import com.course_aura_hub.cart.dto.CheckoutResponse;
import com.course_aura_hub.order.dto.OrderResponse;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.model.OrderGroup;
import com.course_aura_hub.order.payment.PaymentEvent;
import com.course_aura_hub.order.payment.PaymentGateway;
import com.course_aura_hub.order.payment.PaymentSession;

import java.util.List;

public interface OrderService {

    /**
     * Reuses the learner's open order for the course when its price and coupon still match, else opens a new one.
     *
     * @param provider active gateway, or null when none is configured
     */
    Order openOrder(String email, String courseSlug, String couponCode, String currency, String provider);

    /**
     * Opens one order per course in the cart under a single payment. Replaces any open orders for those courses.
     * A coupon applies to one course, see {@code CouponService#chooseCourse}.
     *
     * @param provider active gateway, or null when none is configured
     */
    OrderGroup openGroup(String email, String couponCode, String currency, String provider);

    /** Settles a group that a coupon reduced to zero. */
    CheckoutResponse completeFreeGroup(String groupNumber);

    CheckoutResponse attachGroupSession(String groupNumber, PaymentSession session);

    void markGroupFailed(String groupNumber);

    /** Settles an order that a coupon reduced to zero, which no provider needs to see. */
    OrderResponse completeFreeOrder(String orderNumber);

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
