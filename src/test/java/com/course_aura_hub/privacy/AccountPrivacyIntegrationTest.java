package com.course_aura_hub.privacy;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountPrivacyIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void exportContainsMyCoursesAndAccount() throws Exception {
        TestUser user = registerUser();
        enroll(user, FREE_COURSE);

        mockMvc.perform(as(user, get("/api/v1/me/data-export")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.email").value(user.email()))
                .andExpect(jsonPath("$.data.records.courses[0].slug").value(FREE_COURSE));
    }

    @Test
    void deletionNeedsTheEmailAndPassword() throws Exception {
        TestUser user = registerUser();

        mockMvc.perform(asJson(user, post("/api/v1/me/account/deletion"),
                        Map.of("password", PASSWORD, "confirmation", "someone@else.test")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(user, post("/api/v1/me/account/deletion"),
                        Map.of("password", "Wrong-pass1!", "confirmation", user.email())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletedAccountIsAnonymousAndSignedOut() throws Exception {
        TestUser user = registerUser();
        enroll(user, FREE_COURSE);

        mockMvc.perform(asJson(user, post("/api/v1/me/account/deletion"),
                        Map.of("password", PASSWORD, "confirmation", user.email())))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail(user.email())).isEmpty();
        Integer remaining = jdbcTemplate.queryForObject(
                "select count(*) from students where email = ?", Integer.class, user.email());
        assertThat(remaining).isZero();
        mockMvc.perform(as(user, get("/api/v1/me/profile"))).andExpect(status().isUnauthorized());
    }

    @Test
    void adminsCannotDeleteThemselves() throws Exception {
        TestUser admin = registerAdmin();

        mockMvc.perform(asJson(admin, post("/api/v1/me/account/deletion"),
                        Map.of("password", PASSWORD, "confirmation", admin.email())))
                .andExpect(status().isForbidden());
    }
}
