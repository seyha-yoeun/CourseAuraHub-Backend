package com.course_aura_hub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.demo-data")
public record DemoDataProperties(
        boolean enabled,
        // Shared sign-in password of every demo account
        String password,
        // Database hosts the seed may run against; it wipes the database, so anything else is refused
        List<String> localHosts
) {

    static final String DEFAULT_PASSWORD = "Demo@2026!";
    static final List<String> DEFAULT_LOCAL_HOSTS = List.of("localhost", "127.0.0.1", "[::1]", "host.docker.internal");

    public DemoDataProperties {
        password = password == null || password.isBlank() ? DEFAULT_PASSWORD : password;
        localHosts = localHosts == null || localHosts.isEmpty() ? DEFAULT_LOCAL_HOSTS : List.copyOf(localHosts);
    }
}
