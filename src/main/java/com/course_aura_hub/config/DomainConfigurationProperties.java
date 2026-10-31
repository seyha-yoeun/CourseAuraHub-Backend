package com.course_aura_hub.config;

import com.course_aura_hub.auth.config.AdminBootstrapProperties;
import com.course_aura_hub.notification.config.NotificationProperties;
import com.course_aura_hub.order.config.PaymentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AdminBootstrapProperties.class, DemoDataProperties.class, PaymentProperties.class,
        NotificationProperties.class})
public class DomainConfigurationProperties {
}
