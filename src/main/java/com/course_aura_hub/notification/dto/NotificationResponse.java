package com.course_aura_hub.notification.dto;

import com.course_aura_hub.notification.model.Notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().apiValue(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLink(),
                notification.getReadAt() != null,
                notification.getCreatedAt()
        );
    }
}
