package com.course_aura_hub.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled              accept new subscriptions
 * @param graceDays            days after a period ends that an unpaid renewal keeps access
 * @param pendingTimeoutHours  an unpaid first payment is abandoned after this long
 */
@ConfigurationProperties(prefix = "app.subscriptions")
public record SubscriptionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("3") int graceDays,
        @DefaultValue("24") int pendingTimeoutHours
) {
}
