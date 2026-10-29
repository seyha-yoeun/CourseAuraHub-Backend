package com.course_aura_hub.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIntegrationTest extends AuthIntegrationTestSupport {

    private static final String NEW_PASSWORD = "N3w-P@ssword!";

    @Test
    void forgotPassword_returnsIdenticalResponseForKnownAndUnknownEmails() throws Exception {
        String email = uniqueEmail("forgot");
        registerVerifiedUser(email);
        emailSender.reset();

        String known = forgot(email).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = forgot(uniqueEmail("nobody")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(known, "$.message")).isEqualTo(JsonPath.<String>read(unknown, "$.message"));
        assertThat(emailSender.sentTo(email)).hasSize(1);
        assertThat(emailSender.latestTo(email).orElseThrow().textBody()).contains("/auth/reset-password?token=");
    }

    @Test
    void forgotPassword_isThrottledPerAccount() throws Exception {
        String email = uniqueEmail("throttle");
        registerVerifiedUser(email);
        emailSender.reset();

        forgot(email).andExpect(status().isAccepted());
        forgot(email).andExpect(status().isAccepted());
        assertThat(emailSender.sentTo(email)).hasSize(1);
    }

    @Test
    void forgotPassword_skipsPendingAccounts() throws Exception {
        String email = uniqueEmail("pendingreset");
        register(email, PASSWORD);
        emailSender.reset();

        forgot(email).andExpect(status().isAccepted());
        assertThat(emailSender.sentTo(email)).isEmpty();
    }

    @Test
    void resetPassword_changesPasswordAndRevokesEverySession() throws Exception {
        String email = uniqueEmail("reset");
        Session deviceA = registerVerifiedUser(email);
        Session deviceB = loginSession(email, true);

        forgot(email).andExpect(status().isAccepted());
        String token = emailSender.latestResetToken(email);

        reset(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        login(email, PASSWORD, false).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD, false).andExpect(status().isOk());

        // Refresh tokens and still-valid access tokens of every earlier session are dead
        refresh(deviceA.refreshToken()).andExpect(status().isUnauthorized());
        refresh(deviceB.refreshToken()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + deviceB.accessToken()))
                .andExpect(status().isUnauthorized());

        assertThat(emailSender.latestTo(email).orElseThrow().subject()).contains("password was changed");
    }

    @Test
    void resetToken_isSingleUse() throws Exception {
        String email = uniqueEmail("singleuse");
        registerVerifiedUser(email);
        forgot(email);
        String token = emailSender.latestResetToken(email);

        reset(token, NEW_PASSWORD, null).andExpect(status().isOk());
        reset(token, "An0ther-P@ssword", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired password reset link"));
    }

    @Test
    void expiredResetToken_isRejected() throws Exception {
        String email = uniqueEmail("expiredreset");
        registerVerifiedUser(email);
        forgot(email);
        String token = emailSender.latestResetToken(email);
        jdbcTemplate.update("update auth_one_time_tokens set expires_at = now() - interval '1 minute' "
                + "where user_id = ? and purpose = 'PASSWORD_RESET'", userId(email));

        reset(token, NEW_PASSWORD, null).andExpect(status().isBadRequest());
        login(email, PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    void newResetRequest_supersedesOlderLink() throws Exception {
        String email = uniqueEmail("supersede");
        registerVerifiedUser(email);
        forgot(email);
        String oldToken = emailSender.latestResetToken(email);
        jdbcTemplate.update("update auth_one_time_tokens set created_at = created_at - interval '2 minutes' where user_id = ?",
                userId(email));
        forgot(email);
        String newToken = emailSender.latestResetToken(email);

        reset(oldToken, NEW_PASSWORD, null).andExpect(status().isBadRequest());
        reset(newToken, NEW_PASSWORD, null).andExpect(status().isOk());
    }

    @Test
    void weakOrMismatchedPassword_isRejected() throws Exception {
        String email = uniqueEmail("weakreset");
        registerVerifiedUser(email);
        forgot(email);
        String token = emailSender.latestResetToken(email);

        reset(token, "weak", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
        reset(token, NEW_PASSWORD, "Different-P@ss1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.confirmationMatching").value("Passwords do not match"));

        // Validation failures don't burn the token
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void garbageToken_isRejected() throws Exception {
        reset("not-a-real-token", NEW_PASSWORD, null).andExpect(status().isBadRequest());
    }

    private ResultActions forgot(String email) throws Exception {
        return postJson("/api/v1/auth/forgot-password", Map.of("email", email));
    }

    private ResultActions reset(String token, String password, String confirm) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        body.put("password", password);
        if (confirm != null) {
            body.put("confirmPassword", confirm);
        }
        return postJson("/api/v1/auth/reset-password", body);
    }
}
