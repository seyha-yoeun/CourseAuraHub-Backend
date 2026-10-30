package com.course_aura_hub.learner;

import com.course_aura_hub.auth.AuthIntegrationTestSupport;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.model.Role;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for learner-journey integration tests.
 */
public abstract class LearnerIntegrationTestSupport extends AuthIntegrationTestSupport {

    /** Free, published course seeded by V0017 with a six-lesson curriculum. */
    protected static final String FREE_COURSE = "git-github-fundamentals";
    /** Paid, published course seeded by V0013. */
    protected static final String PAID_COURSE = "nuxt-3-mastery";

    protected record TestUser(String email, String accessToken) {
    }

    /** Registers "Jane Learner", verifies the emailed code and returns the signed-in session. */
    protected TestUser registerUser() throws Exception {
        String email = uniqueEmail("learner");
        postJson("/api/v1/auth/register", new RegisterRequest("Jane", "Learner", email, PASSWORD))
                .andExpect(status().isAccepted());
        String body = verifyEmail(email, emailSender.latestVerificationCode(email))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new TestUser(email, JsonPath.read(body, "$.data.accessToken"));
    }

    protected TestUser registerAdmin() throws Exception {
        TestUser user = registerUser();
        // JwtAuthFilter reloads authorities from the database on every request
        userRepository.findByEmail(user.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_ADMIN);
            userRepository.save(entity);
        });
        return user;
    }

    protected MockHttpServletRequestBuilder as(TestUser user, MockHttpServletRequestBuilder request) {
        return request
                .header("Authorization", "Bearer " + user.accessToken())
                .with(csrf());
    }

    protected MockHttpServletRequestBuilder asJson(TestUser user, MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return as(user, request)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    protected void enroll(TestUser user, String courseSlug) throws Exception {
        mockMvc.perform(asJson(user, post("/api/v1/me/enrollments"), new SelfEnrollBody(courseSlug)))
                .andExpect(status().isCreated());
    }

    protected record SelfEnrollBody(String courseSlug) {
    }
}
