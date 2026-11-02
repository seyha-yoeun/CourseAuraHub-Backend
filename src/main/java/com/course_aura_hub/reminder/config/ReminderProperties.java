package com.course_aura_hub.reminder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param idleDays how long a learner stays quiet before a nudge
 * @param repeatDays the shortest gap between nudges
 * @param batchSize the most nudges in one run; the rest wait for the next run
 */
@ConfigurationProperties(prefix = "app.reminders")
public record ReminderProperties(boolean enabled, int idleDays, int repeatDays, int batchSize) {
}
