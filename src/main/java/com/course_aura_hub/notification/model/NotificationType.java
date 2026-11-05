package com.course_aura_hub.notification.model;

import java.util.Locale;

public enum NotificationType {
    ENROLLED,
    PAYMENT_FAILED,
    ORDER_REFUNDED,
    REFUND_REQUEST,
    CERTIFICATE_ISSUED,
    NEW_LESSON,
    COURSE_UPDATED,
    NEW_ENROLLMENT,
    COURSE_COMPLETED,
    MEMBER_CARD,
    LESSON_QUESTION,
    ASSIGNMENT,
    COURSE_REVIEW,
    COURSE_REMINDER,
    ANNOUNCEMENT,
    WEEKLY_REPORT;

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
