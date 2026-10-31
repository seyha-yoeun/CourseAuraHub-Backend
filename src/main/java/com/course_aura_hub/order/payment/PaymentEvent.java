package com.course_aura_hub.order.payment;

import java.math.BigDecimal;

/**
 * A verified provider notification about one order, or one order group when the number is a group's.
 */
public record PaymentEvent(String orderNumber, Outcome outcome, BigDecimal amount, String currency) {

    public enum Outcome {
        PAID,
        FAILED
    }
}
