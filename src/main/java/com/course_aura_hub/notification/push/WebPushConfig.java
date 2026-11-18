package com.course_aura_hub.notification.push;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WebPushProperties.class)
public class WebPushConfig {
}
