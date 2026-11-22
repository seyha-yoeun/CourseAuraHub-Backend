package com.course_aura_hub.config;

import com.course_aura_hub.auth.security.TokenHasher;
import com.course_aura_hub.auth.security.mfa.Base32;
import com.course_aura_hub.auth.security.mfa.MfaSecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Local demo data: wipes the database once and fills every table from {@code demo/seed.sql}.
 *
 * <p>Runs only when {@code app.demo-data.enabled=true}, only against a database on one of
 * {@code app.demo-data.local-hosts}, and only once: success is recorded in {@code demo_seed_history}, so later
 * restarts keep whatever the database holds. The wipe also removes the sample rows that migrations V0013, V0017 and
 * V0036 insert. It runs before every other runner, so the bootstrap admin is created on top of the seed.</p>
 *
 * <p>Every demo account signs in with {@code app.demo-data.password}, hashed here so no password is stored in the
 * repository. Hash columns the script writes as {@code seed:<raw>} are hashed here with the app's own
 * {@link TokenHasher}, so seeded gift codes, calendar feeds, invites and recovery codes work like real ones.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final String SCRIPT = "demo/seed.sql";
    static final String PASSWORD_PLACEHOLDER = "{{DEMO_PASSWORD_HASH}}";
    static final String MARKER_TABLE = "demo_seed_history";
    static final String SEED_PREFIX = "seed:";
    static final String MFA_EMAIL = "sovann.chhay@demo.courseaurahub.io";
    // Add this to an authenticator app to sign in as the two-factor demo account
    static final String MFA_SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    static final List<String> RECOVERY_CODES = List.of("demo-rc01-2026", "demo-rc02-2026", "demo-rc03-2026", "demo-rc04-2026",
            "demo-rc05-2026", "demo-rc06-2026", "demo-rc07-2026", "demo-rc08-2026", "demo-rc09-2026", "demo-rc10-2026");
    private static final List<String> KEPT_TABLES = List.of("flyway_schema_history", MARKER_TABLE);

    private final DemoDataProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final MfaSecretCipher secretCipher;

    @Override
    public void run(ApplicationArguments args) {
        String host = databaseHost();
        if (properties.localHosts().stream().noneMatch(allowed -> allowed.equalsIgnoreCase(host))) {
            log.warn("Demo data is enabled but the database host '{}' is not local; skipping the seed. Allowed hosts: {}",
                    host, properties.localHosts());
            return;
        }
        if (alreadySeeded()) {
            log.info("Demo data was already seeded on this database; skipping");
            return;
        }
        long started = System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> seed());
        log.info("Demo data seeded in {} ms; every demo account signs in with app.demo-data.password, for example admin@demo.courseaurahub.io",
                Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    /** Wipes every table, runs the script, finishes the hashes and records the run, all in one transaction. */
    void seed() {
        log.warn("Seeding demo data: emptying every table first");
        truncateAll();
        jdbcTemplate.execute(readScript().replace(PASSWORD_PLACEHOLDER, passwordEncoder.encode(properties.password())));
        rehash("access_codes", "id", "code_hash", code -> tokenHasher.hash("access-code", code));
        rehash("calendar_tokens", "user_id", "token_hash", token -> tokenHasher.hash("calendar-feed", token));
        rehash("organization_invites", "id", "token_hash", token -> tokenHasher.hash("organization-invite", token));
        enrollTwoFactorAccount();
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + MARKER_TABLE + " (seeded_at timestamptz NOT NULL DEFAULT now(), script text NOT NULL)");
        jdbcTemplate.update("INSERT INTO " + MARKER_TABLE + " (script) VALUES (?)", SCRIPT);
    }

    private void truncateAll() {
        String kept = KEPT_TABLES.stream().map(name -> "'" + name + "'").collect(Collectors.joining(", "));
        List<String> tables = jdbcTemplate.queryForList(
                "select quote_ident(tablename) from pg_tables where schemaname = 'public' and tablename not in (" + kept + ")",
                String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }

    private void rehash(String table, String key, String column, UnaryOperator<String> hash) {
        List<Object[]> updates = jdbcTemplate.queryForList(
                        "select " + key + " as k, " + column + " as v from " + table + " where " + column + " like ?", SEED_PREFIX + "%")
                .stream()
                .map(row -> new Object[]{hash.apply(((String) row.get("v")).substring(SEED_PREFIX.length())), row.get("k")})
                .toList();
        jdbcTemplate.batchUpdate("update " + table + " set " + column + " = ? where " + key + " = ?", updates);
    }

    /** Turns on two-factor sign-in for the MFA demo account with a known secret and recovery codes. */
    private void enrollTwoFactorAccount() {
        UUID userId = jdbcTemplate.queryForObject("select id from users where email = ?", UUID.class, MFA_EMAIL);
        Timestamp enrolledAt = Timestamp.from(Instant.now().minus(Duration.ofDays(30)));
        jdbcTemplate.batchUpdate("""
                insert into mfa_recovery_codes (id, user_id, code_hash, used_at, created_at) values (?, ?, ?, ?, ?)
                """, RECOVERY_CODES.stream().map(code -> new Object[]{UUID.randomUUID(), userId,
                tokenHasher.hash("MFA_RECOVERY_CODE", userId.toString(), code.replace("-", "").toLowerCase(Locale.ROOT)),
                // The last code was already used once
                code.equals(RECOVERY_CODES.getLast()) ? Timestamp.from(Instant.now().minus(Duration.ofDays(3))) : null,
                enrolledAt}).toList());
        if (!secretCipher.isAvailable()) {
            log.warn("MFA_ENCRYPTION_KEY is not set, so {} keeps two-factor sign-in off", MFA_EMAIL);
            return;
        }
        jdbcTemplate.update("""
                insert into user_totp_credentials (user_id, secret_ciphertext, confirmed_at, last_used_step, created_at)
                values (?, ?, ?, null, ?)
                """, userId, secretCipher.encrypt(Base32.decode(MFA_SECRET)), enrolledAt, enrolledAt);
        jdbcTemplate.update("update users set mfa_enabled = true where id = ?", userId);
        jdbcTemplate.update("""
                insert into auth_audit_events (event_type, outcome, user_id, email, ip_address, metadata, created_at)
                values ('MFA_ENABLED', 'SUCCESS', ?, ?, '10.0.0.77', null, ?)
                """, userId, MFA_EMAIL, enrolledAt);
    }

    private boolean alreadySeeded() {
        Boolean exists = jdbcTemplate.queryForObject("select to_regclass('public." + MARKER_TABLE + "') is not null", Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            return false;
        }
        Boolean seeded = jdbcTemplate.queryForObject("select exists (select 1 from " + MARKER_TABLE + ")", Boolean.class);
        return Boolean.TRUE.equals(seeded);
    }

    /** The host of the JDBC URL, such as {@code localhost} for {@code jdbc:postgresql://localhost:5432/db}. */
    private String databaseHost() {
        String url = jdbcTemplate.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        try {
            String host = URI.create(url == null ? "" : url.replaceFirst("^jdbc:", "")).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String readScript() {
        try (InputStream input = new ClassPathResource(SCRIPT).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read " + SCRIPT, ex);
        }
    }
}
