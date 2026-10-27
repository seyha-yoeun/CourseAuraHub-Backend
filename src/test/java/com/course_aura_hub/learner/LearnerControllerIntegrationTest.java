package com.course_aura_hub.learner;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearnerControllerIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void selfEnroll_freeCourse_appearsInMyCourses_andDuplicateIsConflict() throws Exception {
        TestUser learner = registerUser();

        mockMvc.perform(as(learner, get("/api/v1/me/courses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(asJson(learner, post("/api/v1/me/enrollments"), new SelfEnrollBody(FREE_COURSE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value(FREE_COURSE))
                .andExpect(jsonPath("$.data.progress.progressPercent").value(0));

        mockMvc.perform(as(learner, get("/api/v1/me/courses")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].slug").value(FREE_COURSE))
                .andExpect(jsonPath("$.data[0].enrolledAt").exists())
                .andExpect(jsonPath("$.data[0].progress.completed").value(false));

        mockMvc.perform(asJson(learner, post("/api/v1/me/enrollments"), new SelfEnrollBody(FREE_COURSE)))
                .andExpect(status().isConflict());
    }

    @Test
    void selfEnroll_paidCourse_isRejected() throws Exception {
        TestUser learner = registerUser();

        mockMvc.perform(asJson(learner, post("/api/v1/me/enrollments"), new SelfEnrollBody(PAID_COURSE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(asJson(learner, post("/api/v1/me/enrollments"), new SelfEnrollBody("does-not-exist")))
                .andExpect(status().isNotFound());
    }

    @Test
    void courseDetail_hidesStudentListFromLearners_andReportsEnrollment() throws Exception {
        TestUser learner = registerUser();

        mockMvc.perform(as(learner, get("/api/v1/courses/" + PAID_COURSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolledStudentList").doesNotExist())
                .andExpect(jsonPath("$.data.enrolled").value(false));

        enroll(learner, FREE_COURSE);
        mockMvc.perform(as(learner, get("/api/v1/courses/" + FREE_COURSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(true));

        TestUser admin = registerAdmin();
        mockMvc.perform(as(admin, get("/api/v1/courses/" + PAID_COURSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolledStudentList").isArray());
    }

    @Test
    void profile_updateAndChangePassword() throws Exception {
        TestUser learner = registerUser();

        mockMvc.perform(asJson(learner, patch("/api/v1/me/profile"), Map.of(
                        "firstName", "Janet",
                        "bio", "Lifelong learner",
                        "website", "https://janet.dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Janet"))
                .andExpect(jsonPath("$.data.lastName").value("Learner"))
                .andExpect(jsonPath("$.data.bio").value("Lifelong learner"))
                .andExpect(jsonPath("$.data.hasPassword").value(true));

        mockMvc.perform(asJson(learner, patch("/api/v1/me/profile"), Map.of("firstName", "  ")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asJson(learner, put("/api/v1/me/password"), Map.of(
                        "currentPassword", "Wrong@Pass1",
                        "newPassword", "N3w@Password!")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asJson(learner, put("/api/v1/me/password"), Map.of(
                        "currentPassword", PASSWORD,
                        "newPassword", "weak")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asJson(learner, put("/api/v1/me/password"), Map.of(
                        "currentPassword", PASSWORD,
                        "newPassword", "N3w@Password!")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

        mockMvc.perform(asJson(learner, post("/api/v1/auth/login"), Map.of(
                        "email", learner.email(),
                        "password", "N3w@Password!",
                        "rememberMe", false)))
                .andExpect(status().isOk());
    }

    @Test
    void wishlist_addListRemove_andEnrollmentRemovesItem() throws Exception {
        TestUser learner = registerUser();

        mockMvc.perform(as(learner, put("/api/v1/me/wishlist/" + PAID_COURSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value(PAID_COURSE));
        // Idempotent
        mockMvc.perform(as(learner, put("/api/v1/me/wishlist/" + PAID_COURSE)))
                .andExpect(status().isOk());
        mockMvc.perform(as(learner, put("/api/v1/me/wishlist/" + FREE_COURSE)))
                .andExpect(status().isOk());

        mockMvc.perform(as(learner, get("/api/v1/me/wishlist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(as(learner, delete("/api/v1/me/wishlist/" + PAID_COURSE)))
                .andExpect(status().isOk());

        enroll(learner, FREE_COURSE);

        mockMvc.perform(as(learner, get("/api/v1/me/wishlist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Already enrolled courses cannot be wishlisted
        mockMvc.perform(as(learner, put("/api/v1/me/wishlist/" + FREE_COURSE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/courses"))
                .andExpect(status().isUnauthorized());
    }
}
