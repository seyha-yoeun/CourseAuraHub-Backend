package com.course_aura_hub.order.facade;

import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.order.config.PaymentProperties;
import com.course_aura_hub.order.dto.OrderResponse;
import com.course_aura_hub.order.model.Order;
import com.course_aura_hub.order.payment.PaymentGateway;
import com.course_aura_hub.order.payment.PaymentSession;
import com.course_aura_hub.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Keeps provider calls outside database transactions, so a slow provider never holds row locks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutFacadeImpl implements CheckoutFacade {

    private final OrderService orderService;
    private final ObjectProvider<PaymentGateway> paymentGateway;
    private final PaymentProperties paymentProperties;

    @Override
    public OrderResponse checkout(String email, String courseSlug, String couponCode) {
        PaymentGateway gateway = paymentGateway.getIfAvailable();
        Order order = orderService.openOrder(
                email,
                courseSlug,
                couponCode,
                paymentProperties.currency(),
                gateway == null ? null : gateway.name()
        );
        if (order.getAmount().signum() == 0) {
            return orderService.completeFreeOrder(order.getOrderNumber());
        }
        if (order.getCheckoutUrl() != null) {
            return orderService.findMine(email, order.getOrderNumber());
        }

        PaymentSession session;
        try {
            session = gateway.createPayment(order);
        } catch (RuntimeException ex) {
            log.error("Payment provider failed for order={}", order.getOrderNumber(), ex);
            orderService.markFailed(order.getOrderNumber());
            throw new ServiceUnavailableException("Payment could not be started. Please try again.", ex);
        }
        return orderService.attachSession(order.getOrderNumber(), session);
    }

    @Override
    public void handleWebhook(String provider, String signature, String payload) {
        PaymentGateway gateway = paymentGateway.getIfAvailable();
        if (gateway == null || !gateway.name().equals(provider)) {
            throw ResourceNotFoundException.of("Payment provider", provider);
        }
        orderService.applyPaymentEvent(gateway.parseWebhook(signature, payload));
    }

    @Override
    public OrderResponse refund(String orderNumber) {
        return orderService.refund(orderNumber, requireGateway());
    }

    private PaymentGateway requireGateway() {
        PaymentGateway gateway = paymentGateway.getIfAvailable();
        if (gateway == null) {
            throw new ServiceUnavailableException("Paid checkout is not available yet");
        }
        return gateway;
    }
}
