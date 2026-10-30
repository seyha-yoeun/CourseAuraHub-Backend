package com.course_aura_hub.order.payment;

/**
 * A checkout opened with a payment provider.
 *
 * @param reference   the provider's id for this payment
 * @param checkoutUrl where to send the learner to pay
 */
public record PaymentSession(String reference, String checkoutUrl) {
}
