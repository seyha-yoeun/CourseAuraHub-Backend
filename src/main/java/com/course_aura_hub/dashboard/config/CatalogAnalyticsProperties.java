package com.course_aura_hub.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Anonymous catalog events for the admin funnel.
 *
 * @param enabled          accept events at all
 * @param visitorSalt      secret mixed into visitor hashes, so they can't be matched to accounts or addresses; set
 *                         your own in production
 * @param dedupeWindow     the same visitor's same event on the same course inside this window counts once
 * @param retentionDays    events older than this are deleted
 * @param perMinute        events one client may send per minute
 */
@ConfigurationProperties(prefix = "app.analytics")
public record CatalogAnalyticsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("dev-only-change-me") String visitorSalt,
        @DefaultValue("30m") Duration dedupeWindow,
        @DefaultValue("400") int retentionDays,
        @DefaultValue("60") int perMinute
) {
}
