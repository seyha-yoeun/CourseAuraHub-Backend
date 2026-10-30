package com.course_aura_hub.order;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No provider is configured by default, so paid checkout must fail closed. */
class CheckoutDisabledIntegrationTest extends LearnerIntegrationTestSupport {

    private record OrderBody(String courseSlug) {
    }

    @Test
    void checkoutIsUnavailableWithoutProvider() throws Exception {
        mockMvc.perform(asJson(registerUser(), post("/api/v1/me/orders"), new OrderBody(PAID_COURSE)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void webhookFindsNoProvider() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhooks/fake").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isNotFound());
    }
}
