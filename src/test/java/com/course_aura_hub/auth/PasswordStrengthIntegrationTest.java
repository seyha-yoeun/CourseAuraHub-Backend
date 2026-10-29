package com.course_aura_hub.auth;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordStrengthIntegrationTest extends AuthIntegrationTestSupport {

    @Test
    void register_withWeakPassword_returns400() throws Exception {
        register("weakpass@example.com", "weak")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void register_withNoUppercase_returns400() throws Exception {
        register("noupper@example.com", "password123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_withNoLowercase_returns400() throws Exception {
        register("nolower@example.com", "PASSWORD123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_withNoDigit_returns400() throws Exception {
        register("nodigit@example.com", "Password!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_withNoSpecialChar_returns400() throws Exception {
        register("nospecial@example.com", "Password123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_withWhitespace_returns400() throws Exception {
        register("space@example.com", "Pass word123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_withValidPassword_succeeds() throws Exception {
        register(uniqueEmail("validpass"), "P@ssword123")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void register_withSymbolsOutsideOldWhitelist_succeeds() throws Exception {
        register(uniqueEmail("symbols"), "Pass#word-123")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }
}
