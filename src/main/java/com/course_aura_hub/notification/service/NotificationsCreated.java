package com.course_aura_hub.notification.service;

import com.course_aura_hub.notification.dto.NotificationResponse;

import java.util.Map;
import java.util.UUID;

/** New rows by recipient, delivered to open streams once their transaction commits. */
public record NotificationsCreated(Map<UUID, NotificationResponse> byUser) {
}
