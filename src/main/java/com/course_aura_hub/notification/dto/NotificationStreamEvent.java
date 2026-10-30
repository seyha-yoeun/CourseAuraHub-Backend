package com.course_aura_hub.notification.dto;

/** Payload of a live "notification" event. */
public record NotificationStreamEvent(NotificationResponse notification, long unreadCount) {
}
