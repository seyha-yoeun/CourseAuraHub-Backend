package com.course_aura_hub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-data")
public record DemoDataProperties(
        boolean enabled,
        String file
) {
    public DemoDataProperties {
        file = file == null || file.isBlank() ? "demo/demo-data.json" : file;
    }
}
