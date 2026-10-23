package com.course_aura_hub.auth;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.RefreshTokenRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_thenLogin_thenRefresh_thenLogout() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        String password = "P@ssword123";

        RegisterRequest registerRequest = new RegisterRequest("John", "Doe", email, password);

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        String registerResponse = registerResult.getResponse().getContentAsString();
        ApiResponse<AuthResponse> registerApiResponse = objectMapper.readValue(registerResponse,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));

        String accessToken = registerApiResponse.getData().accessToken();
        String refreshToken = registerApiResponse.getData().refreshToken();

        LoginRequest loginRequest = new LoginRequest(email, password, false);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        ApiResponse<AuthResponse> loginApiResponse = objectMapper.readValue(loginResponse,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));

        String loginAccessToken = loginApiResponse.getData().accessToken();
        String newRefreshToken = loginApiResponse.getData().refreshToken();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + loginAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        // The access token must also be accepted from the HttpOnly cookie
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie("access_token", loginAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        // A refresh token must not be accepted as an access token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + newRefreshToken))
                .andExpect(status().isUnauthorized());

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(newRefreshToken);

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn();

        String refreshResponse = refreshResult.getResponse().getContentAsString();
        ApiResponse<AuthResponse> refreshApiResponse = objectMapper.readValue(refreshResponse,
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, AuthResponse.class));

        String latestRefreshToken = refreshApiResponse.getData().refreshToken();

        // Token rotation must invalidate the refresh token used for the previous exchange
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(newRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        RefreshTokenRequest logoutRequest = new RefreshTokenRequest(latestRefreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        RefreshTokenRequest invalidRefreshRequest = new RefreshTokenRequest(latestRefreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRefreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void refresh_withInvalidToken_returns401() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("not-a-valid-jwt");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_withInvalidPassword_returns400() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("John", "Doe", "test@example.com", "weak");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void register_withDuplicateEmail_returns409() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest("John", "Doe", email, "P@ssword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String email = "wrongpass-" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest("John", "Doe", email, "P@ssword123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(email, "WrongPassword123", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_nonexistentUser_returns401() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nonexistent@example.com", "P@ssword123", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}