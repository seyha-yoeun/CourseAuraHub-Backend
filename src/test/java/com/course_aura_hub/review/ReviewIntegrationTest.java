package com.course_aura_hub.review;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void reviews_requireEnrollment_upsert_andRecalculateRating() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createFreeCourse(admin);

        TestUser first = registerUser();
        mockMvc.perform(asJson(first, put("/api/v1/me/courses/" + slug + "/review"), Map.of("rating", 5)))
                .andExpect(status().isForbidden());

        enroll(first, slug);
        mockMvc.perform(asJson(first, put("/api/v1/me/courses/" + slug + "/review"),
                        Map.of("rating", 5, "comment", "Great course")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.reviewerName").value("Jane Learner"));

        TestUser second = registerUser();
        enroll(second, slug);
        mockMvc.perform(asJson(second, put("/api/v1/me/courses/" + slug + "/review"), Map.of("rating", 4)))
                .andExpect(status().isOk());

        mockMvc.perform(as(first, get("/api/v1/courses/" + slug + "/reviews")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.items[0].reviewerEmail").doesNotExist());

        // Second PUT updates rather than duplicates
        mockMvc.perform(asJson(second, put("/api/v1/me/courses/" + slug + "/review"), Map.of("rating", 1)))
                .andExpect(status().isOk());
        mockMvc.perform(as(first, get("/api/v1/courses/" + slug + "/reviews")))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.averageRating").value(3.0));

        mockMvc.perform(as(second, get("/api/v1/me/courses/" + slug + "/review")))
                .andExpect(jsonPath("$.data.rating").value(1));

        mockMvc.perform(as(second, delete("/api/v1/me/courses/" + slug + "/review")))
                .andExpect(status().isOk());
        mockMvc.perform(as(first, get("/api/v1/courses/" + slug)))
                .andExpect(jsonPath("$.data.rating").value(5.0));
    }

    @Test
    void reviews_validateRating_andAdminCanModerate() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createFreeCourse(admin);
        TestUser learner = registerUser();
        enroll(learner, slug);

        mockMvc.perform(asJson(learner, put("/api/v1/me/courses/" + slug + "/review"), Map.of("rating", 6)))
                .andExpect(status().isBadRequest());

        String body = mockMvc.perform(asJson(learner, put("/api/v1/me/courses/" + slug + "/review"), Map.of("rating", 2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer reviewId = JsonPath.read(body, "$.data.id");

        mockMvc.perform(as(learner, delete("/api/v1/courses/" + slug + "/reviews/" + reviewId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(admin, delete("/api/v1/courses/" + slug + "/reviews/" + reviewId)))
                .andExpect(status().isOk());
        mockMvc.perform(as(learner, get("/api/v1/courses/" + slug + "/reviews")))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private String createFreeCourse(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"), Map.of(
                        "title", "Review Test " + UUID.randomUUID(),
                        "price", 0,
                        "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }
}
