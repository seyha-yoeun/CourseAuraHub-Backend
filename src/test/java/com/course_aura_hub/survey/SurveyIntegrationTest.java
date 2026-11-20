package com.course_aura_hub.survey;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SurveyIntegrationTest extends LearnerIntegrationTestSupport {

    private static final Map<String, Object> SURVEY = Map.of(
            "trigger", "percent_50",
            "questions", List.of(
                    Map.of("text", "How clear were the lessons?", "type", "rating", "required", true),
                    Map.of("text", "Would you take a follow-up course?", "type", "choice",
                            "options", List.of("Yes", "No"), "required", false),
                    Map.of("text", "Anything else?", "type", "text", "required", false)));

    @Test
    void learnersAreAskedHalfWay_andStaffSeeAggregatesOnly() throws Exception {
        TestUser admin = registerAdmin();
        String slug = courseWithLessons(admin, 2);
        mockMvc.perform(asJson(admin, put(manage(slug)), SURVEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].id").value("q1"))
                .andExpect(jsonPath("$.data.recommendQuestion").isNotEmpty());

        TestUser happy = registerUser();
        enroll(happy, slug);
        mockMvc.perform(as(happy, get(learner(slug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligible").value(false));
        answer(happy, slug, 10, Map.of("q1", 4)).andExpect(status().isBadRequest());

        completeFirstLesson(happy, slug);
        mockMvc.perform(as(happy, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].type").value("course_survey"));
        mockMvc.perform(as(happy, get(learner(slug)))).andExpect(jsonPath("$.data.eligible").value(true));

        answer(happy, slug, 10, Map.of("q2", "Maybe")).andExpect(status().isBadRequest());
        answer(happy, slug, 10, Map.of("q1", 4, "q9", "x")).andExpect(status().isBadRequest());
        answer(happy, slug, 10, Map.of("q1", 4, "q2", "Yes", "q3", "Great examples")).andExpect(status().isCreated());
        answer(happy, slug, 10, Map.of("q1", 4)).andExpect(status().isConflict());

        TestUser unhappy = registerUser();
        enroll(unhappy, slug);
        completeFirstLesson(unhappy, slug);
        answer(unhappy, slug, 3, Map.of("q1", 2, "q2", "No")).andExpect(status().isCreated());

        mockMvc.perform(as(admin, get(manage(slug) + "/results")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responses").value(2))
                .andExpect(jsonPath("$.data.nps.score").value(0))
                .andExpect(jsonPath("$.data.nps.promoters").value(1))
                .andExpect(jsonPath("$.data.nps.detractors").value(1))
                .andExpect(jsonPath("$.data.questions[0].average").value(3.0))
                .andExpect(jsonPath("$.data.questions[1].distribution.Yes").value(1))
                .andExpect(jsonPath("$.data.questions[2].comments[0]").value("Great examples"))
                .andExpect(jsonPath("$..email").doesNotExist());
        mockMvc.perform(as(admin, get("/api/v1/instructor/courses/" + slug + "/analytics")))
                .andExpect(jsonPath("$.data.feedback.surveyResponses").value(2))
                .andExpect(jsonPath("$.data.feedback.nps").value(0));
    }

    @Test
    void onlyManagersEditSurveys_andLearnersMustBeEnrolled() throws Exception {
        TestUser admin = registerAdmin();
        String slug = courseWithLessons(admin, 2);
        TestUser learner = registerUser();

        mockMvc.perform(asJson(learner, put(manage(slug)), SURVEY)).andExpect(status().isForbidden());
        mockMvc.perform(asJson(admin, put(manage(slug)), Map.of("trigger", "sometimes", "questions", List.of())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(admin, put(manage(slug)), Map.of("trigger", "completion", "questions",
                        List.of(Map.of("text", "Pick", "type", "choice", "options", List.of("Only one"), "required", false)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(admin, put(manage(slug)), SURVEY)).andExpect(status().isOk());

        mockMvc.perform(as(learner, get(learner(slug)))).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions answer(TestUser user, String slug, int nps,
                                                                      Map<String, Object> answers) throws Exception {
        return mockMvc.perform(asJson(user, post(learner(slug)), Map.of("npsScore", nps, "answers", answers)));
    }

    private void completeFirstLesson(TestUser user, String slug) throws Exception {
        Integer lesson = jdbcTemplate.queryForObject("""
                select l.id from lessons l join course_sections s on s.id = l.section_id join courses c on c.id = s.course_id
                where c.slug = ? order by s.position, l.position limit 1
                """, Integer.class, slug);
        mockMvc.perform(as(user, put("/api/v1/me/lessons/" + lesson + "/completion"))).andExpect(status().isOk());
    }

    private String courseWithLessons(TestUser admin, int count) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Survey " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        String section = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Basics")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        for (int i = 1; i <= count; i++) {
            mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                            Map.of("title", "Lesson " + i, "type", "article", "durationMinutes", 5)))
                    .andExpect(status().isCreated());
        }
        return slug;
    }

    private static String manage(String slug) {
        return "/api/v1/instructor/courses/" + slug + "/survey";
    }

    private static String learner(String slug) {
        return "/api/v1/me/courses/" + slug + "/survey";
    }
}
