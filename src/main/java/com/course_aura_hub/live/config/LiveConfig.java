package com.course_aura_hub.live.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LiveProperties.class)
public class LiveConfig {
}
