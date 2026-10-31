package com.course_aura_hub.order.payment;

import com.course_aura_hub.order.model.Order;

import java.math.BigDecimal;

/**
 * Port to a payment provider. Exactly one implementation is active, chosen by {@code app.payment.provider}.
 *
 * <p>The provider must echo {@link Order#getOrderNumber()} back in its notifications, so a
 * webhook can never race the storing of the provider's own reference.</p>
 */
public interface PaymentGateway {

    /** Provider id, matched against the webhook path. */
    String name();

    PaymentSession createPayment(Order order);

    /**
     * Opens one payment for a group of orders. The provider echoes {@code groupNumber} in its notifications.
     */
    PaymentSession createGroupPayment(String groupNumber, BigDecimal amount, String currency);

    /**
     * Authenticates and parses a webhook.
     *
     * @throws InvalidWebhookException when the signature or payload is not valid
     */
    PaymentEvent parseWebhook(String signature, String payload);

    void refund(Order order);
}
