package com.course_aura_hub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Seeds data for the whole app lifecycle on top of the migration catalog: demo accounts for every role, instructor
 * courses in each review state, progress, certificates, orders, member cards, notifications, learning paths and more.
 *
 * <p>Runs only when {@code app.demo-data.enabled=true}, after {@link DemoDataSeeder}. The script skips itself once its
 * admin account exists, so restarts never duplicate anything. Every demo account signs in with
 * {@code app.demo-data.password}, hashed here so no password or hash is stored in the repository.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoLifecycleSeeder {

    static final String SCRIPT = "demo/lifecycle-seed.sql";
    static final String PASSWORD_PLACEHOLDER = "{{DEMO_PASSWORD_HASH}}";

    private final DemoDataProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    /**
     * Runs the whole script in the caller's transaction (the JSON import's), so it sees the catalog that import
     * wrote and a failure leaves nothing half-seeded.
     */
    @Transactional
    public void seed() {
        String script = readScript().replace(PASSWORD_PLACEHOLDER, passwordEncoder.encode(properties.password()));
        // The script holds PL/pgSQL blocks, so it runs as one multi-statement execution rather than split on ';'
        jdbcTemplate.execute(script);
        log.info("Lifecycle demo data ready; demo accounts sign in with app.demo-data.password, for example admin@demo.courseaurahub.io");
    }

    private String readScript() {
        try (InputStream input = new ClassPathResource(SCRIPT).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read " + SCRIPT, ex);
        }
    }
}
