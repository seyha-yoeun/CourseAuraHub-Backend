package com.course_aura_hub.learner;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resume points, dashboard, streaks, certificates and achievements across a learner's journey.
 */
class LearnerAchievementsIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void newLearner_hasEmptyDashboardAndNoAchievements() throws Exception {
        TestUser learner = registerUser();

        mockMvc.perform(as(learner, get("/api/v1/me/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolledCourses").value(0))
                .andExpect(jsonPath("$.data.lessonsCompleted").value(0))
                .andExpect(jsonPath("$.data.streak.currentDays").value(0))
                .andExpect(jsonPath("$.data.lastSevenDays.length()").value(7))
                .andExpect(jsonPath("$.data.continueLearning").doesNotExist())
                .andExpect(jsonPath("$.data.timeZone").value("UTC"));

        mockMvc.perform(as(learner, get("/api/v1/me/achievements")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.earned == true)]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.code == 'FIRST_STEPS')].target").value(1));

        mockMvc.perform(as(learner, get("/api/v1/me/certificates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void progress_movesResumePointAndFeedsDashboard() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        List<Integer> lessonIds = lessonIds(learner, FREE_COURSE);

        mockMvc.perform(as(learner, get("/api/v1/me/courses")))
                .andExpect(jsonPath("$.data[0].nextLesson.lessonId").value(lessonIds.get(0)))
                .andExpect(jsonPath("$.data[0].nextLesson.sectionTitle").isNotEmpty());

        complete(learner, lessonIds.get(0));

        mockMvc.perform(as(learner, get("/api/v1/me/courses")))
                .andExpect(jsonPath("$.data[0].nextLesson.lessonId").value(lessonIds.get(1)));
        mockMvc.perform(as(learner, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andExpect(jsonPath("$.data.nextLessonId").value(lessonIds.get(1)));

        mockMvc.perform(as(learner, get("/api/v1/me/dashboard").param("timeZone", "Asia/Phnom_Penh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeZone").value("Asia/Phnom_Penh"))
                .andExpect(jsonPath("$.data.enrolledCourses").value(1))
                .andExpect(jsonPath("$.data.inProgressCourses").value(1))
                .andExpect(jsonPath("$.data.lessonsCompleted").value(1))
                .andExpect(jsonPath("$.data.minutesLearned").isNumber())
                .andExpect(jsonPath("$.data.streak.currentDays").value(1))
                .andExpect(jsonPath("$.data.streak.activeToday").value(true))
                .andExpect(jsonPath("$.data.lastSevenDays[6].lessonsCompleted").value(1))
                .andExpect(jsonPath("$.data.continueLearning.slug").value(FREE_COURSE))
                .andExpect(jsonPath("$.data.continueLearning.nextLesson.lessonId").value(lessonIds.get(1)));

        mockMvc.perform(as(learner, get("/api/v1/me/achievements")))
                .andExpect(jsonPath("$.data[?(@.code == 'FIRST_STEPS')].earned").value(true))
                .andExpect(jsonPath("$.data[?(@.code == 'KNOWLEDGE_SEEKER')].progress").value(1))
                .andExpect(jsonPath("$.data[?(@.code == 'KNOWLEDGE_SEEKER')].earned").value(false));
    }

    @Test
    void consecutiveStudyDays_buildStreakAchievements() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        List<Integer> lessonIds = lessonIds(learner, FREE_COURSE);
        for (Integer lessonId : lessonIds.subList(0, 3)) {
            complete(learner, lessonId);
        }

        // Spread the three completions over today, yesterday and the day before
        Integer studentId = jdbcTemplate.queryForObject(
                "SELECT id FROM students WHERE lower(email) = lower(?)", Integer.class, learner.email());
        Instant now = Instant.now();
        for (int daysAgo = 1; daysAgo <= 2; daysAgo++) {
            jdbcTemplate.update("UPDATE lesson_progress SET completed_at = ? WHERE student_id = ? AND lesson_id = ?",
                    Timestamp.from(now.minus(daysAgo, ChronoUnit.DAYS)), studentId, lessonIds.get(daysAgo));
        }

        mockMvc.perform(as(learner, get("/api/v1/me/dashboard")))
                .andExpect(jsonPath("$.data.streak.currentDays").value(3))
                .andExpect(jsonPath("$.data.streak.longestDays").value(3));
        mockMvc.perform(as(learner, get("/api/v1/me/achievements")))
                .andExpect(jsonPath("$.data[?(@.code == 'ON_A_ROLL')].earned").value(true))
                .andExpect(jsonPath("$.data[?(@.code == 'WEEK_WARRIOR')].progress").value(3));
    }

    @Test
    void invalidTimeZone_isRejected() throws Exception {
        TestUser learner = registerUser();

        mockMvc.perform(as(learner, get("/api/v1/me/dashboard").param("timeZone", "Mars/Olympus_Mons")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completingCourse_issuesVerifiableCertificateThatIsKept() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        List<Integer> lessonIds = lessonIds(learner, FREE_COURSE);
        for (Integer lessonId : lessonIds) {
            complete(learner, lessonId);
        }

        String body = mockMvc.perform(as(learner, get("/api/v1/me/certificates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseSlug").value(FREE_COURSE))
                .andExpect(jsonPath("$.data[0].issuedAt").exists())
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(body, "$.data[0].certificateNumber");
        assertThat(number).matches("CAH(-[0-9A-HJKMNP-TV-Z]{4}){4}");

        mockMvc.perform(as(learner, get("/api/v1/me/courses")))
                .andExpect(jsonPath("$.data[0].progress.completed").value(true))
                .andExpect(jsonPath("$.data[0].nextLesson").doesNotExist());
        mockMvc.perform(as(learner, get("/api/v1/me/dashboard")))
                .andExpect(jsonPath("$.data.completedCourses").value(1))
                .andExpect(jsonPath("$.data.certificatesEarned").value(1))
                .andExpect(jsonPath("$.data.continueLearning").doesNotExist());
        mockMvc.perform(as(learner, get("/api/v1/me/achievements")))
                .andExpect(jsonPath("$.data[?(@.code == 'COURSE_FINISHER')].earned").value(true));

        // Anyone can verify, case-insensitively, without seeing the learner's email
        mockMvc.perform(get("/api/v1/certificates/" + number.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateNumber").value(number))
                .andExpect(jsonPath("$.data.learnerName").value("Jane Learner"))
                .andExpect(jsonPath("$.data.courseTitle").isNotEmpty())
                .andExpect(jsonPath("$.data.email").doesNotExist());
        mockMvc.perform(get("/api/v1/certificates/CAH-0000-0000-0000-0000"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/certificates/not-a-number"))
                .andExpect(status().isNotFound());

        // Re-opening the course does not take the certificate away, and re-completing does not issue another
        mockMvc.perform(as(learner, delete("/api/v1/me/lessons/" + lessonIds.getLast() + "/completion")))
                .andExpect(jsonPath("$.data.courseProgress.completed").value(false));
        complete(learner, lessonIds.getLast());
        mockMvc.perform(as(learner, get("/api/v1/me/certificates")))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].certificateNumber").value(number));
    }

    @Test
    void removingLearnersLastUnfinishedLesson_certifiesThem() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        Integer sectionId = JsonPath.read(mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"),
                        Map.of("title", "Intro")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.id");
        Integer finished = createLesson(admin, slug, sectionId, "Lesson one");
        Integer unfinished = createLesson(admin, slug, sectionId, "Lesson two");

        TestUser learner = registerUser();
        enroll(learner, slug);
        complete(learner, finished);
        mockMvc.perform(as(learner, get("/api/v1/me/certificates")))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(as(admin, delete("/api/v1/courses/" + slug + "/lessons/" + unfinished)))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(as(learner, get("/api/v1/me/certificates")))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseSlug").value(slug));
        // The admin, who never enrolled, is not certified
        mockMvc.perform(as(admin, get("/api/v1/me/certificates")))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private void complete(TestUser learner, Integer lessonId) throws Exception {
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonId + "/completion")))
                .andExpect(status().isOk());
    }

    private List<Integer> lessonIds(TestUser user, String slug) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/courses/" + slug + "/curriculum")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.sections[*].lessons[*].id");
    }

    private String createCourse(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"), Map.of(
                        "title", "Certificate Test " + UUID.randomUUID(),
                        "price", 0,
                        "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }

    private Integer createLesson(TestUser admin, String slug, Integer sectionId, String title) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", title, "durationMinutes", 10)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }
}
