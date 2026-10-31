package com.course_aura_hub.notification.model;

import java.util.Locale;

public enum NotificationType {
    ENROLLED,
    PAYMENT_FAILED,
    ORDER_REFUNDED,
    CERTIFICATE_ISSUED,
    NEW_LESSON,
    NEW_ENROLLMENT,
    COURSE_COMPLETED,
    WEEKLY_REPORT;

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
