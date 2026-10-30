package com.course_aura_hub.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param streamTimeout     how long a stream stays open; reconnecting re-runs authentication,
 *                          so this bounds how long a signed-out user can keep receiving events
 * @param maxStreamsPerUser open streams kept per user; the oldest closes first
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        @DefaultValue("15m") Duration streamTimeout,
        @DefaultValue("5") int maxStreamsPerUser
) {
}
