package com.course_aura_hub.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sender identity for outgoing mail. SMTP connection settings live under 'spring.mail'.
 */
@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("no-reply@courseaurahub.local") String from,
        @DefaultValue("CourseAuraHub") String fromName
) {
}
