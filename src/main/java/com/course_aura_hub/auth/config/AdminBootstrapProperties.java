package com.course_aura_hub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security.bootstrap-admin")
public record AdminBootstrapProperties(
        String email,
        String password,
        List<String> promoteEmails
) {
    public AdminBootstrapProperties {
        promoteEmails = promoteEmails == null ? List.of() : List.copyOf(promoteEmails);
    }
}
