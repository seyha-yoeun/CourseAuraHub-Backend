package com.course_aura_hub.curriculum;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LessonPacingIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void sequentialCourse_unlocksEachLessonAfterTheOneBefore() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        int section = createSection(admin, slug);
        int first = createLesson(admin, slug, section, Map.of("title", "One", "type", "article"));
        int second = createLesson(admin, slug, section, Map.of("title", "Two", "type", "video"));
        mockMvc.perform(asJson(admin, put("/api/v1/courses/" + slug + "/curriculum/settings"), Map.of("sequentialLessons", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sequentialLessons").value(true));

        TestUser learner = registerUser();
        enroll(learner, slug);

        mockMvc.perform(as(learner, get(curriculum(slug))))
                .andExpect(jsonPath("$.data.sequentialLessons").value(true))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].locked").value(false))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].locked").value(true))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].lockReason").value("sequence"))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].contentUrl").doesNotExist());

        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + second + "/completion")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, put("/api/v1/me/lessons/" + second + "/position"), Map.of("positionSeconds", 10)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + first + "/completion"))).andExpect(status().isOk());
        mockMvc.perform(as(learner, get(curriculum(slug))))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].locked").value(false))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].lockReason").doesNotExist());
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + second + "/completion"))).andExpect(status().isOk());

        // Staff are never paced
        mockMvc.perform(as(admin, get(curriculum(slug))))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].locked").value(false));
    }

    @Test
    void lockedQuizCantBeStartedOrSubmitted() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        int section = createSection(admin, slug);
        createLesson(admin, slug, section, Map.of("title", "Read first", "type", "article"));
        int quiz = createLesson(admin, slug, section, Map.of("title", "Check", "type", "quiz"));
        mockMvc.perform(asJson(admin, put("/api/v1/admin/lessons/" + quiz + "/quiz"), Map.of(
                        "passPercent", 50, "timeLimitMinutes", 5,
                        "questions", java.util.List.of(Map.of("prompt", "Q", "options", java.util.List.of(
                                Map.of("text", "A", "correct", true), Map.of("text", "B", "correct", false)))))))
                .andExpect(status().isOk());
        mockMvc.perform(asJson(admin, put("/api/v1/courses/" + slug + "/curriculum/settings"), Map.of("sequentialLessons", true)))
                .andExpect(status().isOk());

        TestUser learner = registerUser();
        enroll(learner, slug);
        mockMvc.perform(as(learner, post("/api/v1/me/lessons/" + quiz + "/quiz/start"))).andExpect(status().isForbidden());
        mockMvc.perform(as(learner, get("/api/v1/me/lessons/" + quiz + "/quiz"))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, post("/api/v1/me/lessons/" + quiz + "/quiz/attempts"), Map.of("answers", Map.of(1, 1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void drippedLesson_opensDaysAfterEnrolling() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        int section = createSection(admin, slug);
        int dripped = createLesson(admin, slug, section,
                Map.of("title", "Week two", "type", "article", "releaseAfterDays", 7));

        TestUser learner = registerUser();
        enroll(learner, slug);

        mockMvc.perform(as(learner, get(curriculum(slug))))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].releaseAfterDays").value(7))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].locked").value(true))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].lockReason").value("drip"))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].availableOn").value(LocalDate.now().plusDays(7).toString()));
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + dripped + "/completion")))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("""
                update enrollments set enrolled_at = current_date - 8
                where course_id = (select id from courses where slug = ?)
                  and student_id = (select s.id from students s join users u on u.id = s.user_id where u.email = ?)
                """, slug, learner.email());

        mockMvc.perform(as(learner, get(curriculum(slug))))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].locked").value(false));
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + dripped + "/completion"))).andExpect(status().isOk());
    }

    @Test
    void zeroDelayClearsTheDrip_andInvalidDelaysAreRejected() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        int section = createSection(admin, slug);
        int lesson = createLesson(admin, slug, section, Map.of("title", "Later", "type", "article", "releaseAfterDays", 3));

        mockMvc.perform(asJson(admin, put("/api/v1/courses/" + slug + "/lessons/" + lesson), Map.of("releaseAfterDays", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releaseAfterDays").doesNotExist());
        mockMvc.perform(asJson(admin, put("/api/v1/courses/" + slug + "/lessons/" + lesson), Map.of("releaseAfterDays", -1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void learnersCantChangePacing() throws Exception {
        mockMvc.perform(asJson(registerUser(), put("/api/v1/courses/" + FREE_COURSE + "/curriculum/settings"),
                        Map.of("sequentialLessons", true)))
                .andExpect(status().isForbidden());
    }

    private static String curriculum(String slug) {
        return "/api/v1/courses/" + slug + "/curriculum";
    }

    private String createCourse(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Paced " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }

    private int createSection(TestUser admin, String slug) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Week")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private int createLesson(TestUser admin, String slug, int section, Map<String, Object> fields) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + section + "/lessons"), fields))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }
}
