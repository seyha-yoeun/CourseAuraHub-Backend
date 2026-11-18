package com.course_aura_hub.subscription.service;

import com.course_aura_hub.order.payment.PaymentEvent;
import com.course_aura_hub.order.payment.PaymentEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Receives provider webhooks for SUB- payments from the order module. */
@Component
@RequiredArgsConstructor
public class SubscriptionPaymentHandler implements PaymentEventHandler {

    private final SubscriptionService subscriptionService;

    @Override
    public boolean supports(String paymentNumber) {
        return paymentNumber != null && paymentNumber.startsWith(SubscriptionServiceImpl.PAYMENT_PREFIX);
    }

    @Override
    public void apply(PaymentEvent event) {
        subscriptionService.applyPaymentEvent(event);
    }
}
