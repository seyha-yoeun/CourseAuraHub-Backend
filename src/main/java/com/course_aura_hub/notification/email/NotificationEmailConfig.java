package com.course_aura_hub.notification.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationEmailProperties.class)
public class NotificationEmailConfig {
}
