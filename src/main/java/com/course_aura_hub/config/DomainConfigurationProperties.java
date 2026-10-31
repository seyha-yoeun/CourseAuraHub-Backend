package com.course_aura_hub.config;

import com.course_aura_hub.auth.config.AdminBootstrapProperties;
import com.course_aura_hub.certificate.config.CertificateProperties;
import com.course_aura_hub.notification.config.NotificationProperties;
import com.course_aura_hub.order.config.PaymentProperties;
import com.course_aura_hub.telegram.config.TelegramProperties;
import com.course_aura_hub.file.config.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AdminBootstrapProperties.class, DemoDataProperties.class, PaymentProperties.class,
        NotificationProperties.class, CertificateProperties.class, TelegramProperties.class,
        StorageProperties.class})
public class DomainConfigurationProperties {
}
