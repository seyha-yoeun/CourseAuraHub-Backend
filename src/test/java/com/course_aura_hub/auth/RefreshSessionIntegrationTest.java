package com.course_aura_hub.auth;

import com.course_aura_hub.auth.facade.AuthFacade;
import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.AuthAuditEventRepository;
import com.course_aura_hub.auth.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshSessionIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private AuthFacade authFacade;

    @Autowired
    private AuthAuditEventRepository auditRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void multipleDevices_keepIndependentSessions() throws Exception {
        String email = uniqueEmail("devices");
        registerVerifiedUser(email);
        Session laptop = loginSession(email, false);
        Session phone = loginSession(email, false);

        assertThat(laptop.sessionId()).isNotEqualTo(phone.sessionId());
        Session laptop2 = refreshSession(laptop);
        Session phone2 = refreshSession(phone);

        // Rotation keeps the session id stable and issues new tokens
        assertThat(laptop2.sessionId()).isEqualTo(laptop.sessionId());
        assertThat(laptop2.refreshToken()).isNotEqualTo(laptop.refreshToken());
        assertMeStatus(laptop2, 200);
        assertMeStatus(phone2, 200);
    }

    @Test
    void reuseOfRotatedToken_afterGracePeriod_revokesWholeFamily() throws Exception {
        String email = uniqueEmail("reuse");
        Session original = registerVerifiedUser(email);
        Session rotated = refreshSession(original);
        Session otherDevice = loginSession(email, false);

        // Pretend the rotation happened a minute ago, outside the grace window
        jdbcTemplate.update("update refresh_sessions set revoked_at = revoked_at - interval '1 minute' "
                + "where family_id = ?::uuid and revoked_reason = 'ROTATED'", original.sessionId());

        refresh(original.refreshToken()).andExpect(status().isUnauthorized());

        // The legitimate holder of the newest token is cut off too, but other devices are not
        refresh(rotated.refreshToken()).andExpect(status().isUnauthorized());
        assertMeStatus(rotated, 401);
        assertMeStatus(otherDevice, 200);

        Integer active = jdbcTemplate.queryForObject(
                "select count(*) from refresh_sessions where family_id = ?::uuid and revoked_at is null",
                Integer.class, original.sessionId());
        assertThat(active).isZero();
        awaitAudit(email, AuditEventType.REFRESH_TOKEN_REUSE_DETECTED);
    }

    @Test
    void reuseWithinGracePeriod_isRejectedWithoutRevokingSession() throws Exception {
        Session original = registerVerifiedUser(uniqueEmail("grace"));
        Session rotated = refreshSession(original);

        refresh(original.refreshToken()).andExpect(status().isUnauthorized());
        refreshSession(rotated);
    }

    @Test
    void concurrentRefreshes_produceExactlyOneDescendant() throws Exception {
        String email = uniqueEmail("concurrent");
        Session session = registerVerifiedUser(email);

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            try {
                authFacade.refreshToken(session.refreshToken());
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        };
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(attempt));
        }
        start.countDown();
        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        Integer active = jdbcTemplate.queryForObject(
                "select count(*) from refresh_sessions where family_id = ?::uuid and revoked_at is null",
                Integer.class, session.sessionId());
        assertThat(active).isEqualTo(1);
    }

    @Test
    void logout_signsOutOnlyCurrentDevice() throws Exception {
        String email = uniqueEmail("single-logout");
        registerVerifiedUser(email);
        Session laptop = loginSession(email, false);
        Session phone = loginSession(email, false);

        // Logout using only the access token (no refresh token supplied)
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + laptop.accessToken()))
                .andExpect(status().isOk());

        assertMeStatus(laptop, 401);
        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());
        assertMeStatus(phone, 200);
        refreshSession(phone);
    }

    @Test
    void logoutAll_signsOutEveryDevice() throws Exception {
        String email = uniqueEmail("global-logout");
        registerVerifiedUser(email);
        Session laptop = loginSession(email, false);
        Session phone = loginSession(email, true);

        mockMvc.perform(post("/api/v1/auth/logout-all").header("Authorization", "Bearer " + laptop.accessToken()))
                .andExpect(status().isOk());

        assertMeStatus(laptop, 401);
        assertMeStatus(phone, 401);
        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());
        refresh(phone.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void listAndRevokeIndividualSessions() throws Exception {
        String email = uniqueEmail("list");
        registerVerifiedUser(email);
        Session laptop = loginSession(email, false);
        Session phone = loginSession(email, false);

        String body = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + laptop.accessToken())
                        .header("User-Agent", "JUnit"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data[*].sessionId");
        List<String> currentIds = JsonPath.read(body, "$.data[?(@.current == true)].sessionId");
        assertThat(ids).contains(laptop.sessionId(), phone.sessionId());
        assertThat(currentIds).containsExactly(laptop.sessionId());

        mockMvc.perform(delete("/api/v1/auth/sessions/" + phone.sessionId())
                        .header("Authorization", "Bearer " + laptop.accessToken()))
                .andExpect(status().isOk());
        assertMeStatus(phone, 401);
        assertMeStatus(laptop, 200);
    }

    @Test
    void cannotRevokeAnotherUsersSession() throws Exception {
        Session alice = registerVerifiedUser(uniqueEmail("alice"));
        Session bob = registerVerifiedUser(uniqueEmail("bob"));

        mockMvc.perform(delete("/api/v1/auth/sessions/" + bob.sessionId())
                        .header("Authorization", "Bearer " + alice.accessToken()))
                .andExpect(status().isNotFound());
        assertMeStatus(bob, 200);

        mockMvc.perform(delete("/api/v1/auth/sessions/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + alice.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rememberMe_survivesRotation() throws Exception {
        String email = uniqueEmail("remember");
        registerVerifiedUser(email);
        String loginBody = login(email, PASSWORD, true).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long thirtyDays = 30L * 24 * 60 * 60 * 1000;
        assertThat(JsonPath.<Number>read(loginBody, "$.data.refreshExpiresIn").longValue()).isEqualTo(thirtyDays);

        refresh(toSession(email, loginBody).refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshExpiresIn").value(thirtyDays));
    }

    @Test
    void passwordChange_revokesOtherSessionsAndReturnsNewOne() throws Exception {
        String email = uniqueEmail("change");
        registerVerifiedUser(email);
        Session laptop = loginSession(email, false);
        Session phone = loginSession(email, false);

        String body = mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + laptop.accessToken())
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Changed-P@ss1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertMeStatus(phone, 401);
        refresh(phone.refreshToken()).andExpect(status().isUnauthorized());
        assertMeStatus(toSession(email, body), 200);
        login(email, "Changed-P@ss1", false).andExpect(status().isOk());
    }

    @Test
    void sessionLimit_revokesOldestSession() throws Exception {
        String email = uniqueEmail("limit");
        Session first = registerVerifiedUser(email);
        for (int i = 0; i < 10; i++) {
            loginSession(email, false);
        }
        assertMeStatus(first, 401);
        Integer active = jdbcTemplate.queryForObject(
                "select count(*) from refresh_sessions where user_id = ? and revoked_at is null", Integer.class, userId(email));
        assertThat(active).isEqualTo(10);
    }

    @Test
    void refreshTokensAreStoredOnlyAsHashes() throws Exception {
        String email = uniqueEmail("hashes");
        Session session = registerVerifiedUser(email);
        List<String> stored = jdbcTemplate.queryForList(
                "select token_hash from refresh_sessions where user_id = ?", String.class, userId(email));
        assertThat(stored).isNotEmpty().noneMatch(hash -> hash.equals(session.refreshToken()));
    }

    @Test
    void legacyRefreshToken_isMigratedToASessionOnce() throws Exception {
        String email = uniqueEmail("legacy");
        registerVerifiedUser(email);
        User user = userRepository.findByEmail(email).orElseThrow();

        // Shape of refresh tokens issued before multi-device sessions: no "sid", hash stored on users
        Instant now = Instant.now();
        String legacyToken = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(Map.of("userId", user.getId().toString(), "tokenType", "REFRESH"))
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(7, ChronoUnit.DAYS)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_JWT_SECRET)))
                .compact();
        user.setRefreshToken(jwtTokenProvider.hashRefreshToken(legacyToken));
        userRepository.save(user);

        refresh(legacyToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").exists());
        assertThat(userRepository.findByEmail(email).orElseThrow().getRefreshToken()).isNull();
        refresh(legacyToken).andExpect(status().isUnauthorized());
    }

    private void assertMeStatus(Session session, int expected) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().is(expected));
    }

    private void awaitAudit(String email, AuditEventType type) throws InterruptedException {
        UUID userId = userId(email);
        for (int i = 0; i < 50; i++) {
            if (auditRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().anyMatch(e -> e.getEventType() == type)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Audit event " + type + " not recorded");
    }
}
