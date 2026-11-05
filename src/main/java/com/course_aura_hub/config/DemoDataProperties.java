package com.course_aura_hub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-data")
public record DemoDataProperties(
        boolean enabled,
        String file,
        // Shared sign-in password of the lifecycle demo accounts
        String password
) {

    static final String DEFAULT_PASSWORD = "Demo@2026!";

    public DemoDataProperties {
        file = file == null || file.isBlank() ? "demo/demo-data.json" : file;
        password = password == null || password.isBlank() ? DEFAULT_PASSWORD : password;
    }
}
