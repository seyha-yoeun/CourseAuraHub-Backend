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
 * Starts the app with demo data enabled on its own database, so the seed never leaks into the shared test database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestEmailConfiguration.class)
class DemoLifecycleSeedIntegrationTest {

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
    private DemoLifecycleSeeder seeder;

    @Test
    void everyRoleCanSignIn_butUnverifiedAndDisabledAccountsCannot() throws Exception {
        for (String email : List.of("admin@demo.courseaurahub.io", "sokha.chan@demo.courseaurahub.io",
                "lina.meas@demo.courseaurahub.io", "vannak.kim@demo.courseaurahub.io", "bopha.lim@demo.courseaurahub.io")) {
            login(email).andExpect(status().isOk());
        }
        login("pending.signup@demo.courseaurahub.io").andExpect(status().is4xxClientError());
        login("disabled.account@demo.courseaurahub.io").andExpect(status().is4xxClientError());
    }

    @Test
    void everyLifecycleTableHasData() {
        for (String table : List.of("users", "students", "courses", "course_sections", "lessons", "quiz_questions", "assignments",
                "assignment_submissions", "lesson_progress", "lesson_watch_positions", "lesson_notes", "lesson_questions",
                "lesson_answers", "quiz_attempts", "quiz_attempt_grants", "course_certificates", "course_reviews", "wishlist_items",
                "cart_items", "orders", "order_groups", "coupons", "course_discounts", "member_card_applications", "member_cards",
                "member_card_transactions", "member_card_top_up_requests", "notifications", "course_announcements",
                "instructor_profiles", "learning_paths", "learning_path_courses", "learning_path_certificates",
                "learner_reminders", "admin_audit_events")) {
            assertThat(count("select count(*) from " + table)).as(table).isPositive();
        }
        assertThat(count("select count(distinct status) from orders")).isEqualTo(5);
        assertThat(count("select count(*) from courses where status = 'PENDING_REVIEW'")).isPositive();
        assertThat(count("select count(*) from courses where status = 'DRAFT' and review_note is not null")).isPositive();
    }

    @Test
    void derivedValuesStayConsistent() {
        // Card balances match their ledgers
        assertThat(count("""
                select count(*) from member_cards c
                where c.balance <> coalesce((select t.balance_after from member_card_transactions t
                                             where t.card_id = c.id order by t.created_at desc, t.id desc limit 1), 0)
                """)).isZero();
        // Lesson totals match the curriculum on every course the seed gave lessons to
        assertThat(count("""
                select count(*) from courses c
                where c.slug in ('python-for-data-analysis', 'ux-research-essentials', 'typescript-for-vue-devs', 'docker-kubernetes-devops')
                  and c.total_lessons <> (select count(*) from lessons l join course_sections s on s.id = l.section_id where s.course_id = c.id)
                """)).isZero();
        // Every completed course of a demo learner has its certificate
        assertThat(count("""
                select count(*) from student_courses sc join students s on s.id = sc.student_id
                where sc.completed and s.email like '%@demo.courseaurahub.io'
                  and not exists (select 1 from course_certificates cc where cc.student_id = sc.student_id and cc.course_id = sc.course_id)
                """)).isZero();
        // Every enrollment link has its ledger row
        assertThat(count("""
                select count(*) from student_courses sc join students s on s.id = sc.student_id
                where s.user_id is not null
                  and not exists (select 1 from enrollments e where e.student_id = sc.student_id and e.course_id = sc.course_id)
                """)).isZero();
    }

    @Test
    void theApiShowsEachLifecycleState() throws Exception {
        String learner = token("vannak.kim@demo.courseaurahub.io");
        mockMvc.perform(get("/api/v1/me/courses").header("Authorization", "Bearer " + learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
        // Sequential course: the lesson after Vannak's progress is open, the one after that waits
        String curriculum = mockMvc.perform(get("/api/v1/courses/python-for-data-analysis/curriculum").header("Authorization", "Bearer " + learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sequentialLessons").value(true))
                .andReturn().getResponse().getContentAsString();
        List<String> reasons = JsonPath.read(curriculum, "$.data.sections[*].lessons[*].lockReason");
        assertThat(reasons).contains("sequence");

        String instructor = token("sokha.chan@demo.courseaurahub.io");
        mockMvc.perform(get("/api/v1/instructor/courses/python-for-data-analysis/analytics").header("Authorization", "Bearer " + instructor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.learners.enrolled").value(8))
                .andExpect(jsonPath("$.data.assignments.pendingReview").value(1));

        mockMvc.perform(get("/api/v1/learning-paths")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/v1/instructors/sokha-chan")).andExpect(status().isOk());
    }

    @Test
    void runningTheSeedAgainChangesNothing() {
        Map<String, Object> before = jdbcTemplate.queryForMap(SNAPSHOT);
        seeder.seed();
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
