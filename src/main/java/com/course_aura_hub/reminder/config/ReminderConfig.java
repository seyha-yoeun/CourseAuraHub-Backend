package com.course_aura_hub.reminder.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReminderProperties.class)
public class ReminderConfig {
}
