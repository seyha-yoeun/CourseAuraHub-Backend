package com.course_aura_hub.config;

import com.course_aura_hub.auth.IntegrationTestBase;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.support.TestEmailConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the app with demo data enabled on its own database, so the seed (which empties every table) never touches
 * the shared test database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestEmailConfiguration.class)
class DemoDataSeedIntegrationTest {

    private static final String PASSWORD = "Demo@2026!";

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("course_aura_demo")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("jwt.secret", () -> IntegrationTestBase.TEST_JWT_SECRET);
        registry.add("app.rate-limit.enabled", () -> "false");
        registry.add("app.security.auth.mfa.encryption-key", () -> IntegrationTestBase.TEST_MFA_KEY);
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
        registry.add("app.demo-data.enabled", () -> "true");
        registry.add("app.demo-data.password", () -> PASSWORD);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DemoDataSeeder seeder;

    @Test
    void everyRoleCanSignIn_butUnverifiedDisabledAndLockedAccountsCannot() throws Exception {
        for (String email : List.of("admin@demo.courseaurahub.io", "support@demo.courseaurahub.io", "sokha.chan@demo.courseaurahub.io",
                "vannak.kim@demo.courseaurahub.io", "sreymom.chea@demo.courseaurahub.io", "bopha.lim@demo.courseaurahub.io")) {
            login(email).andExpect(status().isOk());
        }
        login("pending.signup@demo.courseaurahub.io").andExpect(status().is4xxClientError());
        login("disabled.account@demo.courseaurahub.io").andExpect(status().is4xxClientError());
        login("locked.account@demo.courseaurahub.io").andExpect(status().is4xxClientError());
    }

    @Test
    void everyTableHasData_andTheMigrationSamplesAreGone() {
        List<String> tables = jdbcTemplate.queryForList("""
                select tablename from pg_tables where schemaname = 'public'
                  and tablename not in ('flyway_schema_history', 'demo_seed_history')
                """, String.class);
        assertThat(tables).hasSizeGreaterThan(90);
        for (String table : tables) {
            assertThat(count("select count(*) from " + table)).as(table).isPositive();
        }
        assertThat(count("select count(*) from users where email not like '%@demo.courseaurahub.io'")).isZero();
        assertThat(count("select count(distinct status) from orders")).isEqualTo(5);
        assertThat(count("select count(distinct status) from subscriptions")).isEqualTo(5);
        assertThat(count("select count(distinct status) from courses")).isEqualTo(4);
    }

    @Test
    void derivedValuesStayConsistent() {
        // Card balances match their ledgers
        assertThat(count("""
                select count(*) from member_cards c
                where c.balance <> coalesce((select t.balance_after from member_card_transactions t
                                             where t.card_id = c.id order by t.created_at desc, t.id desc limit 1), 0)
                """)).isZero();
        // Course counters match the rows they count
        assertThat(count("""
                select count(*) from courses c
                where c.total_students <> (select count(*) from student_courses sc where sc.course_id = c.id)
                   or c.total_lessons <> (select count(*) from lessons l join course_sections s on s.id = l.section_id where s.course_id = c.id)
                   or c.revenue <> coalesce((select sum(amount) from enrollments e where e.course_id = c.id), 0)
                """)).isZero();
        // Every completed course has its certificate
        assertThat(count("""
                select count(*) from student_courses sc
                where sc.completed and not exists (select 1 from course_certificates cc where cc.student_id = sc.student_id and cc.course_id = sc.course_id)
                """)).isZero();
        // Gated lessons were only completed by passing them
        assertThat(count("""
                select count(*) from lesson_progress lp join lessons l on l.id = lp.lesson_id and l.type = 'QUIZ'
                where not exists (select 1 from quiz_attempts a where a.student_id = lp.student_id and a.lesson_id = l.id and a.passed)
                """)).isZero();
        assertThat(count("""
                select count(*) from lesson_progress lp join code_exercises e on e.lesson_id = lp.lesson_id
                where not exists (select 1 from code_submissions s where s.student_id = lp.student_id and s.lesson_id = lp.lesson_id and s.passed)
                """)).isZero();
        // Nothing happened in the future
        assertThat(count("select count(*) from lesson_progress where completed_at > now()")).isZero();
        assertThat(count("select count(*) from notifications where created_at > now()")).isZero();
        // Every hash placeholder was replaced
        assertThat(count("select count(*) from access_codes where code_hash like 'seed:%'")).isZero();
        assertThat(count("select count(*) from calendar_tokens where token_hash like 'seed:%'")).isZero();
        assertThat(count("select count(*) from organization_invites where token_hash like 'seed:%'")).isZero();
    }

    @Test
    void theApiShowsTheSeededJourneys() throws Exception {
        String learner = token("vannak.kim@demo.courseaurahub.io");
        mockMvc.perform(get("/api/v1/me/courses").header("Authorization", "Bearer " + learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
        String curriculum = mockMvc.perform(get("/api/v1/courses/python-for-data-analysis/curriculum").header("Authorization", "Bearer " + learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sequentialLessons").value(true))
                .andReturn().getResponse().getContentAsString();
        String videoUrl = JsonPath.read(curriculum, "$.data.sections[0].lessons[0].contentUrl");
        assertThat(videoUrl).contains("youtube.com/watch?v=lYm8KEuWeNk");

        // The seeded calendar token was hashed with the app's own hasher, so the feed opens
        mockMvc.perform(get("/api/v1/calendar/vannak-calendar-demo-token-2026.ics")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/learning-paths")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4));
        mockMvc.perform(get("/api/v1/instructors/sokha-chan")).andExpect(status().isOk());
    }

    @Test
    void runningTheSeederAgainChangesNothing() throws Exception {
        Map<String, Object> before = jdbcTemplate.queryForMap(SNAPSHOT);
        seeder.run(null);
        assertThat(jdbcTemplate.queryForMap(SNAPSHOT)).isEqualTo(before);
    }

    private static final String SNAPSHOT = """
            select (select count(*) from users) users, (select count(*) from orders) orders,
                   (select count(*) from notifications) notifications, (select count(*) from lessons) lessons,
                   (select sum(total_students) from courses) students
            """;

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD, false))));
    }

    private String token(String email) throws Exception {
        String body = login(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }
}
