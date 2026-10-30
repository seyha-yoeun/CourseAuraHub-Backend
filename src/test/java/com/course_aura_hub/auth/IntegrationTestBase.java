package com.course_aura_hub.auth;

import com.course_aura_hub.support.RecordingEmailSender;
import com.course_aura_hub.support.TestEmailConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestEmailConfiguration.class)
public abstract class IntegrationTestBase {

    // Test-only keys; never used outside the test JVM
    public static final String TEST_JWT_SECRET = "P7VIN9hw3AsKhz+tPEqcaZgeWAbmCy5ayl0gWY4s4EEaJ1wK6lgn+ir5Mn+qQC7I";
    public static final String TEST_MFA_KEY = "Q8p+J5ZW1zgtx2SwDOi8Fv9t9IlbYeunPBZ03kGvepI=";

    // Singleton container shared by every test class (and the cached Spring context); Ryuk removes it at JVM exit
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("course_aura_db")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        postgres.start();
    }

    @Autowired
    protected RecordingEmailSender emailSender;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("jwt.access-token-expiration", () -> "900000");
        registry.add("jwt.refresh-token-expiration", () -> "604800000");
        registry.add("app.rate-limit.enabled", () -> "false");
        registry.add("app.security.auth.mfa.encryption-key", () -> TEST_MFA_KEY);
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
    }

    @BeforeEach
    void resetEmailSender() {
        emailSender.reset();
    }
}
