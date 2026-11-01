package com.course_aura_hub.student;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentAccountIntegrationTest extends LearnerIntegrationTestSupport {

    private Integer studentIdOf(TestUser user) {
        return jdbcTemplate.queryForObject("select id from students where email = ?", Integer.class, user.email());
    }

    @Test
    void studentDetail_linksToTheSignInAccount() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        String userId = jdbcTemplate.queryForObject("select id::text from users where email = ?", String.class, learner.email());

        mockMvc.perform(as(registerAdmin(), get("/api/v1/students/" + studentIdOf(learner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId));
    }

    @Test
    void studentDetail_isAdminOnly() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);

        mockMvc.perform(as(learner, get("/api/v1/students/" + studentIdOf(learner))))
                .andExpect(status().isForbidden());
    }
}
