package com.course_aura_hub.auth;

import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.exception.InvalidOneTimeCodeException;
import com.course_aura_hub.auth.facade.AuthFacade;
import com.course_aura_hub.auth.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationVerificationIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired
    private AuthFacade authFacade;

    @Test
    void register_createsPendingUserAndEmailsHashedCode() throws Exception {
        String email = uniqueEmail("pending");

        register(email, PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.codeLength").value(6))
                .andExpect(jsonPath("$.data.codeExpiresInSeconds").value(600));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.isPendingEmailVerification()).isTrue();

        String code = emailSender.latestVerificationCode(email);
        assertThat(code).matches("\\d{6}");
        String storedHash = jdbcTemplate.queryForObject(
                "select token_hash from auth_one_time_tokens where user_id = ? and purpose = 'EMAIL_VERIFICATION'",
                String.class, user.getId());
        assertThat(storedHash).isNotBlank().doesNotContain(code);
    }

    @Test
    void unverifiedUser_cannotLogInOrUseApi() throws Exception {
        String email = uniqueEmail("unverified");
        register(email, PASSWORD).andExpect(status().isAccepted());

        login(email, PASSWORD, false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("verification")));

        // Wrong password on a pending account looks like any other bad login
        login(email, "Wrong-P@ss1", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void correctCode_verifiesEmailAndSignsIn() throws Exception {
        String email = uniqueEmail("verify");
        register(email, PASSWORD).andExpect(status().isAccepted());

        String body = verifyEmail(email, emailSender.latestVerificationCode(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + toSession(email, body).accessToken()))
                .andExpect(status().isOk());
        postJson("/api/v1/auth/login", new LoginRequest(email, PASSWORD, false)).andExpect(status().isOk());
    }

    @Test
    void wrongCode_isRejectedWithGenericMessage() throws Exception {
        String email = uniqueEmail("wrongcode");
        register(email, PASSWORD).andExpect(status().isAccepted());
        String code = emailSender.latestVerificationCode(email);

        verifyEmail(email, otherCode(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code"));

        // Unknown emails get the identical answer
        verifyEmail(uniqueEmail("nobody"), code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code"));

        verifyEmail(email, code).andExpect(status().isOk());
    }

    @Test
    void usedCode_cannotBeReused() throws Exception {
        String email = uniqueEmail("reuse");
        register(email, PASSWORD).andExpect(status().isAccepted());
        String code = emailSender.latestVerificationCode(email);

        verifyEmail(email, code).andExpect(status().isOk());
        verifyEmail(email, code).andExpect(status().isBadRequest());
    }

    @Test
    void expiredCode_isRejected() throws Exception {
        String email = uniqueEmail("expired");
        register(email, PASSWORD).andExpect(status().isAccepted());
        String code = emailSender.latestVerificationCode(email);

        jdbcTemplate.update("update auth_one_time_tokens set expires_at = now() - interval '1 minute' where user_id = ?",
                userId(email));

        verifyEmail(email, code).andExpect(status().isBadRequest());
        assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void tooManyWrongAttempts_revokeTheCode() throws Exception {
        String email = uniqueEmail("attempts");
        register(email, PASSWORD).andExpect(status().isAccepted());
        String code = emailSender.latestVerificationCode(email);

        for (int i = 0; i < 5; i++) {
            verifyEmail(email, otherCode(code)).andExpect(status().isBadRequest());
        }
        // Even the right code is refused once the attempt budget is spent
        verifyEmail(email, code).andExpect(status().isBadRequest());

        Integer attempts = jdbcTemplate.queryForObject(
                "select attempts from auth_one_time_tokens where user_id = ? and purpose = 'EMAIL_VERIFICATION'",
                Integer.class, userId(email));
        assertThat(attempts).isEqualTo(5);
    }

    @Test
    void resend_supersedesPreviousCode() throws Exception {
        String email = uniqueEmail("resend");
        register(email, PASSWORD).andExpect(status().isAccepted());
        String firstCode = emailSender.latestVerificationCode(email);
        expireCooldown(email);

        postJson("/api/v1/auth/verify-email/resend", Map.of("email", email)).andExpect(status().isAccepted());
        String secondCode = emailSender.latestVerificationCode(email);
        assertThat(emailSender.sentTo(email)).hasSize(2);

        if (!firstCode.equals(secondCode)) {
            verifyEmail(email, firstCode).andExpect(status().isBadRequest());
        }
        verifyEmail(email, secondCode).andExpect(status().isOk());
    }

    @Test
    void resend_respectsCooldownAndHourlyCap_withGenericResponses() throws Exception {
        String email = uniqueEmail("cooldown");
        register(email, PASSWORD).andExpect(status().isAccepted());

        // Within the cooldown: accepted, but no new email
        String throttled = postJson("/api/v1/auth/verify-email/resend", Map.of("email", email))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(emailSender.sentTo(email)).hasSize(1);

        // Hourly cap (5): four more sends after clearing the cooldown each time, then nothing
        for (int i = 0; i < 6; i++) {
            expireCooldown(email);
            postJson("/api/v1/auth/verify-email/resend", Map.of("email", email)).andExpect(status().isAccepted());
        }
        assertThat(emailSender.sentTo(email)).hasSize(5);

        String unknown = postJson("/api/v1/auth/verify-email/resend", Map.of("email", uniqueEmail("ghost")))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(unknown, "$.message"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(throttled, "$.message"));
    }

    @Test
    void reRegisteringPendingAccount_replacesPasswordAndCode() throws Exception {
        String email = uniqueEmail("squat");
        register(email, "Squatter-P@ss1").andExpect(status().isAccepted());
        String squatterCode = emailSender.latestVerificationCode(email);
        expireCooldown(email);

        register(email, PASSWORD).andExpect(status().isAccepted());
        String ownerCode = emailSender.latestVerificationCode(email);

        if (!squatterCode.equals(ownerCode)) {
            verifyEmail(email, squatterCode).andExpect(status().isBadRequest());
        }
        verifyEmail(email, ownerCode).andExpect(status().isOk());
        login(email, "Squatter-P@ss1", false).andExpect(status().isUnauthorized());
        login(email, PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    void emailDeliveryFailure_returns503AndKeepsAccountPending() throws Exception {
        String email = uniqueEmail("smtpdown");
        emailSender.setFailing(true);

        register(email, PASSWORD)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SMTP"))));

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        login(email, PASSWORD, false).andExpect(status().isForbidden());
    }

    @Test
    void concurrentVerification_consumesCodeExactlyOnce() throws Exception {
        String email = uniqueEmail("race");
        register(email, PASSWORD).andExpect(status().isAccepted());
        String code = emailSender.latestVerificationCode(email);

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        Callable<Boolean> attempt = () -> {
            start.await();
            try {
                authFacade.verifyEmail(email, code);
                return true;
            } catch (InvalidOneTimeCodeException ex) {
                return false;
            }
        };
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
        Integer sessions = jdbcTemplate.queryForObject(
                "select count(*) from refresh_sessions where user_id = ?", Integer.class, userId(email));
        assertThat(sessions).isEqualTo(1);
    }

    private void expireCooldown(String email) {
        jdbcTemplate.update("update auth_one_time_tokens set created_at = created_at - interval '2 minutes' where user_id = ?",
                userId(email));
    }

    private static String otherCode(String code) {
        char last = code.charAt(code.length() - 1);
        return code.substring(0, code.length() - 1) + (last == '9' ? '0' : (char) (last + 1));
    }
}
