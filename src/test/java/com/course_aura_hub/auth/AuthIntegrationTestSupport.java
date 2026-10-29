package com.course_aura_hub.auth;

import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.RefreshTokenRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for auth integration tests: registration with email verification, login and refresh.
 */
@AutoConfigureMockMvc
public abstract class AuthIntegrationTestSupport extends IntegrationTestBase {

    protected static final String PASSWORD = "P@ssword123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected record Session(String email, String accessToken, String refreshToken, String sessionId) {
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions register(String email, String password) throws Exception {
        return postJson("/api/v1/auth/register", new RegisterRequest("John", "Doe", email, password));
    }

    protected ResultActions verifyEmail(String email, String code) throws Exception {
        return postJson("/api/v1/auth/verify-email", Map.of("email", email, "code", code));
    }

    protected ResultActions login(String email, String password, boolean rememberMe) throws Exception {
        return postJson("/api/v1/auth/login", new LoginRequest(email, password, rememberMe));
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/refresh", new RefreshTokenRequest(refreshToken));
    }

    /** Registers, verifies the emailed code and returns the session created by verification. */
    protected Session registerVerifiedUser(String email) throws Exception {
        register(email, PASSWORD).andExpect(status().isAccepted());
        String body = verifyEmail(email, emailSender.latestVerificationCode(email))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return toSession(email, body);
    }

    protected Session loginSession(String email, boolean rememberMe) throws Exception {
        String body = login(email, PASSWORD, rememberMe)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return toSession(email, body);
    }

    protected Session refreshSession(Session session) throws Exception {
        String body = refresh(session.refreshToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return toSession(session.email(), body);
    }

    protected Session toSession(String email, String responseBody) {
        return new Session(
                email,
                JsonPath.read(responseBody, "$.data.accessToken"),
                JsonPath.read(responseBody, "$.data.refreshToken"),
                JsonPath.read(responseBody, "$.data.sessionId")
        );
    }

    protected UUID userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }
}
