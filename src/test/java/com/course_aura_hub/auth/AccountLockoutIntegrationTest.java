package com.course_aura_hub.auth;

import com.course_aura_hub.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountLockoutIntegrationTest extends AuthIntegrationTestSupport {

    @Test
    void login_after5FailedAttempts_accountLocked() throws Exception {
        String email = uniqueEmail("lockout");
        registerVerifiedUser(email);

        LoginRequest wrongLoginRequest = new LoginRequest(email, "WrongPassword123", false);

        for (int i = 0; i < 5; i++) {
            MvcResult result = postJson("/api/v1/auth/login", wrongLoginRequest)
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            if (i == 4) {
                String response = result.getResponse().getContentAsString();
                assertThat(response).contains("locked");
            }
        }

        postJson("/api/v1/auth/login", new LoginRequest(email, PASSWORD, false))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("locked")));
    }
}
