package com.course_aura_hub.moderation;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewModerationIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void courseStaffReplyToReviews_andTheReviewerHearsAboutIt() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        TestUser learner = registerUser();
        int review = review(learner, slug, 4, "Good, but needs more exercises");

        mockMvc.perform(asJson(admin, put(replyPath(slug, review)), Map.of("body", "Thanks! Exercises are coming next month.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply.body").value("Thanks! Exercises are coming next month."));
        mockMvc.perform(get("/api/v1/courses/" + slug + "/reviews"))
                .andExpect(jsonPath("$.data.items[0].reply.authorName").value("Jane Learner"));
        mockMvc.perform(as(learner, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].title").value("Reply to your review"));

        TestUser instructor = registerUser();
        userRepository.findByEmail(instructor.email()).ifPresent(user -> {
            user.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(user);
        });
        mockMvc.perform(asJson(instructor, put(replyPath(slug, review)), Map.of("body", "Not my course"))).andExpect(status().isForbidden());
        mockMvc.perform(as(admin, delete(replyPath(slug, review)))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses/" + slug + "/reviews")).andExpect(jsonPath("$.data.items[0].reply").doesNotExist());
    }

    @Test
    void helpfulVotesAreOnePerPerson_andSortReviews() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        TestUser first = registerUser();
        TestUser second = registerUser();
        int older = review(first, slug, 5, "Very helpful course");
        review(second, slug, 3, "It was fine");

        TestUser voter = registerUser();
        mockMvc.perform(as(voter, put("/api/v1/me/reviews/" + older + "/helpful"))).andExpect(jsonPath("$.data.helpfulCount").value(1));
        mockMvc.perform(as(voter, put("/api/v1/me/reviews/" + older + "/helpful"))).andExpect(jsonPath("$.data.helpfulCount").value(1));
        mockMvc.perform(as(first, put("/api/v1/me/reviews/" + older + "/helpful"))).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/courses/" + slug + "/reviews").param("sort", "helpful"))
                .andExpect(jsonPath("$.data.items[0].id").value(older));
        mockMvc.perform(get("/api/v1/courses/" + slug + "/reviews"))
                .andExpect(jsonPath("$.data.items[0].id").value(org.hamcrest.Matchers.not(older)));
        mockMvc.perform(as(voter, delete("/api/v1/me/reviews/" + older + "/helpful"))).andExpect(jsonPath("$.data.helpfulCount").value(0));
    }

    @Test
    void reportedReviewsCanBeHidden_whichAlsoTakesThemOutOfTheRating() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        review(registerUser(), slug, 5, "Great course");
        int abusive = review(registerUser(), slug, 1, "Terrible, buy my course instead!");
        assertThat(rating(slug)).isEqualTo(3.0);

        TestUser reporter = registerUser();
        report(reporter, "review", abusive).andExpect(status().isAccepted());
        report(reporter, "review", abusive).andExpect(status().isAccepted());
        report(registerUser(), "review", abusive).andExpect(status().isAccepted());

        String queue = mockMvc.perform(as(admin, get("/api/v1/admin/reports").param("size", "100")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(queue, "$.data.items[?(@.targetId == " + abusive + " && @.targetType == 'review')].id");
        assertThat(ids).hasSize(2);
        List<String> excerpts = JsonPath.read(queue, "$.data.items[?(@.targetId == " + abusive + ")].excerpt");
        assertThat(excerpts.getFirst()).contains("buy my course");

        mockMvc.perform(as(admin, post("/api/v1/admin/reports/" + ids.getFirst() + "/hide")))
                .andExpect(jsonPath("$.data.status").value("hidden"));
        mockMvc.perform(as(admin, post("/api/v1/admin/reports/" + ids.get(1) + "/hide"))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/courses/" + slug + "/reviews")).andExpect(jsonPath("$.data.totalElements").value(1));
        assertThat(rating(slug)).isEqualTo(5.0);
    }

    @Test
    void reportsNeedAKnownTarget_andOnlyAdminsModerate() throws Exception {
        TestUser learner = registerUser();
        report(learner, "comment", 1).andExpect(status().isBadRequest());
        report(learner, "review", 999999).andExpect(status().isNotFound());
        mockMvc.perform(as(learner, get("/api/v1/admin/reports"))).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions report(TestUser user, String type, int id) throws Exception {
        return mockMvc.perform(asJson(user, post("/api/v1/me/reports"), Map.of("targetType", type, "targetId", id, "reason", "Spam")));
    }

    private int review(TestUser learner, String slug, int rating, String comment) throws Exception {
        enroll(learner, slug);
        String body = mockMvc.perform(asJson(learner, put("/api/v1/me/courses/" + slug + "/review"), Map.of("rating", rating, "comment", comment)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private double rating(String slug) throws Exception {
        Number value = JsonPath.read(mockMvc.perform(get("/api/v1/courses/" + slug)).andReturn().getResponse().getContentAsString(), "$.data.rating");
        return value.doubleValue();
    }

    private static String replyPath(String slug, int review) {
        return "/api/v1/instructor/courses/" + slug + "/reviews/" + review + "/reply";
    }

    private String createCourse(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"), Map.of("title", "Reviewed " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }
}
