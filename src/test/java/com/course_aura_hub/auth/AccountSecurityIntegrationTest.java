package com.course_aura_hub.auth;

import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.AuthProvider;
import com.course_aura_hub.auth.model.AuthAuditEvent;
import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.AuthAuditEventRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class AccountSecurityIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private AuthAuditEventRepository auditRepository;

    @Test
    void disablingAccount_revokesSessionsAndBlocksLogin() throws Exception {
        Session admin = registerAdmin();
        String email = uniqueEmail("disable");
        Session target = registerVerifiedUser(email);

        mockMvc.perform(patch("/api/v1/admin/users/" + userId(email) + "/status")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        meStatus(target, 401);
        refresh(target.refreshToken()).andExpect(status().isUnauthorized());
        login(email, PASSWORD, false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is unavailable. Please contact support."));
        // Without the password, a disabled account is indistinguishable from a bad login
        login(email, "Wrong-P@ss1", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        awaitAudit(userId(email), AuditEventType.ACCOUNT_STATUS_CHANGED);
    }

    @Test
    void adminEndpoints_requireAdminRole() throws Exception {
        Session user = registerVerifiedUser(uniqueEmail("plain"));
        mockMvc.perform(patch("/api/v1/admin/users/" + UUID.randomUUID() + "/status")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockedAccount_rejectsAccessTokenAndRefresh_untilAdminUnlocks() throws Exception {
        Session admin = registerAdmin();
        String email = uniqueEmail("locked");
        Session session = registerVerifiedUser(email);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        userRepository.save(user);

        meStatus(session, 401);
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/users/" + user.getId() + "/unlock")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk());
        meStatus(session, 200);
    }

    @Test
    void oauthOnlyAccount_passwordLoginGetsGenericError() throws Exception {
        String email = uniqueEmail("oauthonly");
        userRepository.save(User.builder()
                .firstName("Oauth").lastName("Only").email(email)
                .provider(AuthProvider.GOOGLE).providerId("g-" + UUID.randomUUID())
                .emailVerified(true).build());

        login(email, PASSWORD, false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void cors_allowsConfiguredOriginOnly() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void cookieAuthenticatedWrites_requireCsrfToken() throws Exception {
        Session session = registerVerifiedUser(uniqueEmail("csrf"));
        Cookie accessCookie = new Cookie("access_token", session.accessToken());

        mockMvc.perform(post("/api/v1/auth/logout-all").cookie(accessCookie))
                .andExpect(status().isForbidden());
        meStatus(session, 200);

        mockMvc.perform(post("/api/v1/auth/logout-all").cookie(accessCookie).with(csrf()))
                .andExpect(status().isOk());
        meStatus(session, 401);
    }

    @Test
    void csrfEndpoint_issuesToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    void auditTrail_recordsLoginOutcomesWithoutSecrets() throws Exception {
        String email = uniqueEmail("audit");
        registerVerifiedUser(email);
        login(email, "Wrong-P@ss1", false).andExpect(status().isUnauthorized());
        login(email, PASSWORD, false).andExpect(status().isOk());

        UUID id = userId(email);
        awaitAudit(id, AuditEventType.SESSION_CREATED);
        List<AuthAuditEvent> events = awaitEvents(id, 5);
        assertThat(events).extracting(AuthAuditEvent::getEventType)
                .contains(AuditEventType.REGISTRATION, AuditEventType.EMAIL_VERIFICATION, AuditEventType.LOGIN);
        assertThat(events).anyMatch(e -> e.getEventType() == AuditEventType.LOGIN && "BAD_CREDENTIALS".equals(e.getReason()));
        assertThat(events).allSatisfy(e -> {
            assertThat(String.valueOf(e.getMetadata())).doesNotContain(PASSWORD);
            assertThat(String.valueOf(e.getReason())).doesNotContain(PASSWORD);
        });
    }

    @Test
    void sensitiveValues_neverAppearInLogs(CapturedOutput output) throws Exception {
        String email = uniqueEmail("logs");
        String password = "L0g-Secret-P@ss";
        register(email, password);
        String verifyBody = verifyEmail(email, emailSender.latestVerificationCode(email))
                .andReturn().getResponse().getContentAsString();
        Session session = toSession(email, verifyBody);
        Session rotated = refreshSession(session);
        postJson("/api/v1/auth/forgot-password", Map.of("email", email));
        String resetToken = emailSender.latestResetToken(email);
        postJson("/api/v1/auth/reset-password", Map.of("token", resetToken, "password", "N3w-L0g-P@ss!"));
        login(email, "N3w-L0g-P@ss!", false);

        assertThat(output.getAll())
                .doesNotContain(password)
                .doesNotContain("N3w-L0g-P@ss!")
                .doesNotContain(session.accessToken())
                .doesNotContain(session.refreshToken())
                .doesNotContain(rotated.refreshToken())
                .doesNotContain(resetToken);
    }

    private Session registerAdmin() throws Exception {
        String email = uniqueEmail("admin");
        Session session = registerVerifiedUser(email);
        User admin = userRepository.findByEmail(email).orElseThrow();
        // JwtAuthFilter reloads authorities from the database on every request
        admin.setRole(Role.ROLE_ADMIN);
        userRepository.save(admin);
        return session;
    }

    private void meStatus(Session session, int expected) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().is(expected));
    }

    private void awaitAudit(UUID userId, AuditEventType type) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (auditRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().anyMatch(e -> e.getEventType() == type)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Audit event " + type + " not recorded");
    }

    private List<AuthAuditEvent> awaitEvents(UUID userId, int minimum) throws InterruptedException {
        List<AuthAuditEvent> events = List.of();
        for (int i = 0; i < 50 && events.size() < minimum; i++) {
            events = auditRepository.findByUserIdOrderByCreatedAtDesc(userId);
            Thread.sleep(100);
        }
        return events;
    }
}
