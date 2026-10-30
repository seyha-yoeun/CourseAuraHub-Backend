package com.course_aura_hub.auth;

import com.course_aura_hub.auth.security.mfa.Base32;
import com.course_aura_hub.auth.security.mfa.TotpGenerator;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MfaIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private TotpGenerator totp;

    private record Enrollment(byte[] secret, List<String> recoveryCodes) {
    }

    @Test
    void enrollment_isNotEnforcedUntilConfirmed() throws Exception {
        String email = uniqueEmail("mfa-pending");
        Session session = registerVerifiedUser(email);

        authed(session, post("/api/v1/auth/mfa/totp/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")));

        login(email, PASSWORD, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
        authed(session, get("/api/v1/auth/mfa/status"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.enrollmentPending").value(true));
    }

    @Test
    void confirmWithWrongCode_doesNotEnable() throws Exception {
        Session session = registerVerifiedUser(uniqueEmail("mfa-wrong"));
        authed(session, post("/api/v1/auth/mfa/totp/setup")).andExpect(status().isOk());

        authedJson(session, "/api/v1/auth/mfa/totp/confirm", Map.of("code", "000000"))
                .andExpect(status().isBadRequest());
        authed(session, get("/api/v1/auth/mfa/status")).andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void loginWithMfa_requiresSecondFactorAndRejectsReplay() throws Exception {
        String email = uniqueEmail("mfa-login");
        Session session = registerVerifiedUser(email);
        Enrollment enrollment = enroll(session);

        String challengeBody = login(email, PASSWORD, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String mfaToken = JsonPath.read(challengeBody, "$.data.mfaToken");

        // Enrollment consumed the current step; the next step is still inside the drift window
        String code = totp.generate(enrollment.secret(), totp.timeStep(Instant.now()) + 1);
        String authBody = mfaVerify(Map.of("mfaToken", mfaToken, "code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + toSession(email, authBody).accessToken()))
                .andExpect(status().isOk());

        // The challenge is single-use, and the same TOTP code cannot be replayed on a new challenge
        mfaVerify(Map.of("mfaToken", mfaToken, "code", code)).andExpect(status().isBadRequest());
        String secondChallenge = JsonPath.read(login(email, PASSWORD, false).andReturn().getResponse().getContentAsString(),
                "$.data.mfaToken");
        mfaVerify(Map.of("mfaToken", secondChallenge, "code", code)).andExpect(status().isBadRequest());
    }

    @Test
    void recoveryCode_worksOnce() throws Exception {
        String email = uniqueEmail("mfa-recovery");
        Session session = registerVerifiedUser(email);
        Enrollment enrollment = enroll(session);
        String recoveryCode = enrollment.recoveryCodes().get(0);

        String token = challenge(email);
        mfaVerify(Map.of("mfaToken", token, "recoveryCode", recoveryCode.toUpperCase())).andExpect(status().isOk());

        mfaVerify(Map.of("mfaToken", challenge(email), "recoveryCode", recoveryCode)).andExpect(status().isBadRequest());
        authed(session, get("/api/v1/auth/mfa/status"))
                .andExpect(jsonPath("$.data.remainingRecoveryCodes").value(9));
    }

    @Test
    void wrongCodes_exhaustChallengeAndCountTowardsLockout() throws Exception {
        String email = uniqueEmail("mfa-brute");
        Session session = registerVerifiedUser(email);
        enroll(session);
        String token = challenge(email);

        for (int i = 0; i < 4; i++) {
            mfaVerify(Map.of("mfaToken", token, "code", "000000")).andExpect(status().isBadRequest());
        }
        // Fifth failure reaches the account lockout threshold
        mfaVerify(Map.of("mfaToken", token, "code", "000000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("locked")));
        login(email, PASSWORD, false).andExpect(status().isUnauthorized());
    }

    @Test
    void disable_requiresPasswordAndCode() throws Exception {
        String email = uniqueEmail("mfa-disable");
        Session session = registerVerifiedUser(email);
        Enrollment enrollment = enroll(session);

        authedJson(session, "/api/v1/auth/mfa/disable", Map.of("code", enrollment.recoveryCodes().get(1), "password", "Wrong-P@ss1"))
                .andExpect(status().isBadRequest());
        authedJson(session, "/api/v1/auth/mfa/disable", Map.of("code", enrollment.recoveryCodes().get(1), "password", PASSWORD))
                .andExpect(status().isOk());

        login(email, PASSWORD, false).andExpect(jsonPath("$.data.accessToken").exists());
        assertThat(emailSender.latestTo(email).orElseThrow().subject()).contains("disabled");
    }

    @Test
    void totpSecret_isEncryptedAtRest() throws Exception {
        String email = uniqueEmail("mfa-at-rest");
        Session session = registerVerifiedUser(email);
        String body = authed(session, post("/api/v1/auth/mfa/totp/setup")).andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(body, "$.data.secret");

        String stored = jdbcTemplate.queryForObject(
                "select secret_ciphertext from user_totp_credentials where user_id = ?", String.class, userId(email));
        assertThat(stored).doesNotContain(secret);
    }

    private Enrollment enroll(Session session) throws Exception {
        String setup = authed(session, post("/api/v1/auth/mfa/totp/setup")).andReturn().getResponse().getContentAsString();
        byte[] secret = Base32.decode(JsonPath.read(setup, "$.data.secret"));
        String code = totp.generate(secret, totp.timeStep(Instant.now()));
        String confirm = authedJson(session, "/api/v1/auth/mfa/totp/confirm", Map.of("code", code))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> codes = JsonPath.read(confirm, "$.data.recoveryCodes");
        assertThat(codes).hasSize(10);
        return new Enrollment(secret, codes);
    }

    private String challenge(String email) throws Exception {
        return JsonPath.read(login(email, PASSWORD, false).andReturn().getResponse().getContentAsString(), "$.data.mfaToken");
    }

    private ResultActions mfaVerify(Map<String, String> body) throws Exception {
        return postJson("/api/v1/auth/mfa/verify", body);
    }

    private ResultActions authed(Session session, MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + session.accessToken()));
    }

    private ResultActions authedJson(Session session, String url, Object body) throws Exception {
        return authed(session, post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
