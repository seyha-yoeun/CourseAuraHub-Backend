package com.course_aura_hub.notification.email;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.notification.model.NotificationType;

import java.util.Locale;

/** How a notification type reaches someone by email. */
public enum EmailMode {
    INSTANT,
    DIGEST,
    OFF;

    public static EmailMode fromValue(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessRuleException("Email mode must be instant, digest or off");
        }
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * What a type does before the user chooses. Money, certificates and grades arrive straight away; everything
     * else waits for the daily digest. The weekly report already has its own email.
     */
    public static EmailMode defaultFor(NotificationType type) {
        return switch (type) {
            case CERTIFICATE_ISSUED, ORDER_REFUNDED, PAYMENT_FAILED, MEMBER_CARD, ASSIGNMENT -> INSTANT;
            case WEEKLY_REPORT -> OFF;
            default -> DIGEST;
        };
    }
}
