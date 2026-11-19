package com.course_aura_hub.order.payment;

/**
 * Lets another module own payments that aren't course orders, such as subscriptions. Webhooks still arrive through
 * the order module, which hands a verified event to the handler that recognises its number.
 */
public interface PaymentEventHandler {

    /** Whether this handler owns the payment with this number, typically by its prefix. */
    boolean supports(String paymentNumber);

    /** Applies a verified event inside the webhook's transaction. Must be safe to repeat. */
    void apply(PaymentEvent event);
}
