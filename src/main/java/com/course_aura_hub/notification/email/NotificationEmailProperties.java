package com.course_aura_hub.notification.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param enabled  sends notification emails at all; the in-app feed is unaffected
 * @param appUrl   frontend origin that notification links open in
 * @param apiUrl   public backend origin, for the one-click unsubscribe link
 * @param digestMaxItems notifications listed in one digest; the rest stay in the app
 */
@ConfigurationProperties(prefix = "app.notifications.email")
public record NotificationEmailProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:3000") String appUrl,
        @DefaultValue("http://localhost:8080") String apiUrl,
        @DefaultValue("20") int digestMaxItems
) {
}
