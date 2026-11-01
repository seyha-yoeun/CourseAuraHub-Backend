package com.course_aura_hub.order.payment;

import com.course_aura_hub.common.exception.BusinessRuleException;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** How a learner chose to pay at checkout. */
public enum PaymentMethod {
    // The configured payment provider
    ONLINE,
    MEMBER_CARD;

    /** Stored as an order's provider, so refunds know to return the money to the card. */
    public static final String MEMBER_CARD_PROVIDER = "member_card";

    public static PaymentMethod fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return ONLINE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unsupported payment method: " + value);
        }
    }
}
