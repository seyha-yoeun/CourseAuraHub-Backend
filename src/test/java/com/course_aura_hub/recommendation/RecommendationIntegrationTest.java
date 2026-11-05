package com.course_aura_hub.recommendation;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendationIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void related_ranksSharedTagsFirstAndHidesTheCourseItself() throws Exception {
        TestUser admin = registerAdmin();
        String tag = "rec-" + System.nanoTime();
        String source = createCourse(admin, "Source " + tag, "published", List.of(tag, tag + "-b"));
        String match = createCourse(admin, "Match " + tag, "published", List.of(tag, tag + "-b"));
        String draft = createCourse(admin, "Draft " + tag, "draft", List.of(tag, tag + "-b"));

        String body = mockMvc.perform(get("/api/v1/courses/" + source + "/related").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value(match))
                .andExpect(jsonPath("$.data[0].revenue").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        List<String> slugs = JsonPath.read(body, "$.data[*].slug");
        assertThat(slugs).doesNotContain(source, draft);
        List<String> statuses = JsonPath.read(body, "$.data[*].status");
        assertThat(statuses).containsOnly("published");
    }

    @Test
    void related_clampsLimitAndHidesUnpublishedSource() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + FREE_COURSE + "/related").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        String draft = createCourse(registerAdmin(), "Hidden " + System.nanoTime(), "draft", List.of());
        mockMvc.perform(get("/api/v1/courses/" + draft + "/related"))
                .andExpect(status().isNotFound());
    }

    @Test
    void learnerRecommendations_skipEnrolledAndWishlistedCourses() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        mockMvc.perform(as(learner, put("/api/v1/me/wishlist/" + PAID_COURSE)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(as(learner, get("/api/v1/me/recommendations")).param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> slugs = JsonPath.read(body, "$.data[*].slug");
        assertThat(slugs).isNotEmpty().doesNotContain(FREE_COURSE, PAID_COURSE);
    }

    @Test
    void newLearner_getsPopularCourses_andVisitorIsRejected() throws Exception {
        mockMvc.perform(as(registerUser(), get("/api/v1/me/recommendations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));

        mockMvc.perform(get("/api/v1/me/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    private String createCourse(TestUser admin, String title, String status, List<String> tags) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", title, "status", status, "tags", tags)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }
}
