package com.course_aura_hub.auth;

import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.RefreshTokenRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends AuthIntegrationTestSupport {

    @Test
    void register_thenVerify_thenLogin_thenRefresh_thenLogout() throws Exception {
        String email = uniqueEmail("test");

        register(email, PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verificationRequired").value(true))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verifyEmail(email, emailSender.latestVerificationCode(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(header().exists("Set-Cookie"));

        Session session = loginSession(email, false);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        // The access token must also be accepted from the HttpOnly cookie
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie("access_token", session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        // A refresh token must not be accepted as an access token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + session.refreshToken()))
                .andExpect(status().isUnauthorized());

        Session refreshed = refreshSession(session);

        // Token rotation must invalidate the refresh token used for the previous exchange
        refresh(session.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        postJson("/api/v1/auth/logout", new RefreshTokenRequest(refreshed.refreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        refresh(refreshed.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void logout_revokesAccessTokenBeforeItExpires() throws Exception {
        Session session = registerVerifiedUser(uniqueEmail("revoke"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk());

        postJson("/api/v1/auth/logout", new RefreshTokenRequest(session.refreshToken()))
                .andExpect(status().isOk());

        // The access token has not expired yet, but logout must have revoked it
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void refresh_withInvalidToken_returns401() throws Exception {
        refresh("not-a-valid-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_withInvalidPassword_returns400() throws Exception {
        register("test@example.com", "weak")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void register_withDuplicateEmail_returnsSameGenericResponse() throws Exception {
        String email = uniqueEmail("duplicate");
        registerVerifiedUser(email);
        emailSender.reset();

        String first = register(uniqueEmail("fresh"), PASSWORD)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String duplicate = postJson("/api/v1/auth/register", new RegisterRequest("Eve", "Attacker", email, "An0ther-P@ss"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        // Same message and shape: the response does not reveal that the email is taken
        assertThat(JsonPath.<String>read(duplicate, "$.message"))
                .isEqualTo(JsonPath.<String>read(first, "$.message"));
        // The real owner is told instead, and their password is untouched
        assertThat(emailSender.latestTo(email).orElseThrow().subject()).contains("Sign-up attempt");
        loginSession(email, false);
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String email = uniqueEmail("wrongpass");
        registerVerifiedUser(email);

        postJson("/api/v1/auth/login", new LoginRequest(email, "WrongPassword123", false))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_nonexistentUser_returnsSameErrorAsWrongPassword() throws Exception {
        postJson("/api/v1/auth/login", new LoginRequest("nonexistent@example.com", PASSWORD, false))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
