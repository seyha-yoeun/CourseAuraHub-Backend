package com.course_aura_hub.config;

import com.course_aura_hub.auth.config.AdminBootstrapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AdminBootstrapProperties.class, DemoDataProperties.class})
public class DomainConfigurationProperties {
}
