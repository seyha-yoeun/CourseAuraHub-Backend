package com.course_aura_hub.refund.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RefundPolicyProperties.class)
public class RefundConfig {
}
