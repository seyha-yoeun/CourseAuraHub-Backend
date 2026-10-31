package com.course_aura_hub.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param verifyUrl frontend page that checks a certificate; the number is appended as a path segment
 */
@ConfigurationProperties(prefix = "app.certificates")
public record CertificateProperties(
        @DefaultValue("http://localhost:3000/certificates") String verifyUrl
) {
}
