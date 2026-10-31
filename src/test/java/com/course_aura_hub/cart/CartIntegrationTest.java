package com.course_aura_hub.cart;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.order.payment.FakePaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret"
})
class CartIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String CART = "/api/v1/me/cart";
    private static final String WEBHOOK = "/api/v1/payments/webhooks/fake";

    @Autowired
    private FakePaymentGateway gateway;

    private String paidCourse(TestUser admin, int price) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"), Map.of(
                        "title", "Cart Test " + UUID.randomUUID(),
                        "price", price,
                        "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }

    private void add(TestUser user, String slug) throws Exception {
        mockMvc.perform(as(user, put(CART + "/" + slug))).andExpect(status().isOk());
    }

    private String checkout(TestUser user) throws Exception {
        return mockMvc.perform(asJson(user, post(CART + "/checkout"), Map.of()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions webhook(String number, String outcome, String amount) throws Exception {
        String payload = number + ":" + outcome + ":" + amount + ":USD";
        return mockMvc.perform(post(WEBHOOK)
                .contentType(MediaType.TEXT_PLAIN)
                .header("X-Payment-Signature", gateway.sign(payload))
                .content(payload));
    }

    @Test
    void cart_addsOnceAndRemoves() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        String slug = paidCourse(admin, 30);

        add(learner, slug);
        add(learner, slug);
        String cart = mockMvc.perform(as(learner, get(CART)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(JsonPath.read(cart, "$.data.total").toString())).isEqualByComparingTo("30");

        mockMvc.perform(as(learner, delete(CART + "/" + slug))).andExpect(status().isOk());
        mockMvc.perform(as(learner, get(CART))).andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void cart_rejectsFreeCourses() throws Exception {
        mockMvc.perform(as(registerUser(), put(CART + "/" + FREE_COURSE))).andExpect(status().isBadRequest());
    }

    @Test
    void emptyCart_cannotCheckOut() throws Exception {
        mockMvc.perform(asJson(registerUser(), post(CART + "/checkout"), Map.of())).andExpect(status().isBadRequest());
    }

    @Test
    void onePayment_enrollsEveryCourseAndEmptiesTheCart() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        add(learner, paidCourse(admin, 30));
        add(learner, paidCourse(admin, 20));

        String body = checkout(learner);
        String group = JsonPath.read(body, "$.data.groupNumber");
        List<String> statuses = JsonPath.read(body, "$.data.orders[*].status");
        assertThat(group).startsWith("GRP-");
        assertThat(statuses).containsOnly("PENDING");
        assertThat(new BigDecimal(JsonPath.read(body, "$.data.amount").toString())).isEqualByComparingTo("50");

        // Unpaid checkouts stay in the cart
        mockMvc.perform(as(learner, get(CART))).andExpect(jsonPath("$.data.items.length()").value(2));

        webhook(group, "PAID", "50.00").andExpect(status().isOk());
        // Providers redeliver webhooks
        webhook(group, "PAID", "50.00").andExpect(status().isOk());

        mockMvc.perform(as(learner, get("/api/v1/me/courses"))).andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(as(learner, get(CART))).andExpect(jsonPath("$.data.items").isEmpty());
        String orders = mockMvc.perform(as(learner, get("/api/v1/me/orders")))
                .andReturn().getResponse().getContentAsString();
        List<String> paid = JsonPath.read(orders, "$.data[*].status");
        assertThat(paid).containsExactly("PAID", "PAID");
    }

    @Test
    void wrongAmount_enrollsNothing() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        add(learner, paidCourse(admin, 30));
        add(learner, paidCourse(admin, 20));
        String group = JsonPath.read(checkout(learner), "$.data.groupNumber");

        webhook(group, "PAID", "30.00").andExpect(status().isBadRequest());

        mockMvc.perform(as(learner, get("/api/v1/me/courses"))).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void cancellingOneOrder_cancelsItsWholePayment() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        add(learner, paidCourse(admin, 30));
        add(learner, paidCourse(admin, 20));
        String number = JsonPath.read(checkout(learner), "$.data.orders[0].orderNumber");

        mockMvc.perform(as(learner, post("/api/v1/me/orders/" + number + "/cancel"))).andExpect(status().isOk());

        String orders = mockMvc.perform(as(learner, get("/api/v1/me/orders")))
                .andReturn().getResponse().getContentAsString();
        List<String> statuses = JsonPath.read(orders, "$.data[*].status");
        assertThat(statuses).containsOnly("CANCELLED");
    }

    @Test
    void checkingOutAgain_replacesTheOpenPayment() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        add(learner, paidCourse(admin, 30));

        String first = JsonPath.read(checkout(learner), "$.data.groupNumber");
        String second = JsonPath.read(checkout(learner), "$.data.groupNumber");

        assertThat(second).isNotEqualTo(first);
    }
}
