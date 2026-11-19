package com.course_aura_hub.certificate.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CertificateSharingProperties.class)
public class CertificateSharingConfig {
}
