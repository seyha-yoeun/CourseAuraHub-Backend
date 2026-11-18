package com.course_aura_hub.subscription;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.order.payment.FakePaymentGateway;
import com.course_aura_hub.subscription.facade.SubscriptionFacade;
import com.course_aura_hub.subscription.service.SubscriptionService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret",
        "app.subscriptions.grace-days=3"
})
class SubscriptionIntegrationTest extends LearnerIntegrationTestSupport {

    @Autowired
    private FakePaymentGateway gateway;
    @Autowired
    private SubscriptionFacade subscriptionFacade;
    @Autowired
    private SubscriptionService subscriptionService;

    @Test
    void anOnlineSubscriberEnrollsFree_andLosesTheCourseWhenItEnds() throws Exception {
        String plan = createPlan(registerAdmin(), "9.99");
        mockMvc.perform(get("/api/v1/subscription-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == '" + plan + "')].price").value(9.99));

        TestUser learner = registerUser();
        String body = subscribe(learner, plan, "online")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.checkoutUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String payment = JsonPath.read(body, "$.data.payments[0].paymentNumber");
        assertThat(payment).startsWith("SUB-");
        subscribe(learner, plan, "online").andExpect(status().isCreated());

        // The second sign-up replaced the unpaid first one
        String current = currentPayment(learner);
        webhook(current, "9.99").andExpect(status().isOk());
        mockMvc.perform(as(learner, get("/api/v1/me/subscription")))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.hasAccess").value(true));
        subscribe(learner, plan, "online").andExpect(status().isConflict());

        mockMvc.perform(asJson(learner, post("/api/v1/me/subscription/enrollments"), Map.of("courseSlug", PAID_COURSE)))
                .andExpect(status().isCreated());
        mockMvc.perform(as(learner, get("/api/v1/me/courses")))
                .andExpect(jsonPath("$.data[?(@.slug == '" + PAID_COURSE + "')]").isNotEmpty());
        BigDecimal spent = jdbcTemplate.queryForObject(
                "select s.total_spent from students s join users u on u.id = s.user_id where u.email = ?",
                BigDecimal.class, learner.email());
        assertThat(spent).isEqualByComparingTo("0");

        mockMvc.perform(as(learner, post("/api/v1/me/subscription/cancel")))
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true))
                .andExpect(jsonPath("$.data.hasAccess").value(true));
        endPeriod(learner, "1 minute");
        subscriptionService.expireDue();

        mockMvc.perform(as(learner, get("/api/v1/me/subscription"))).andExpect(jsonPath("$.data.status").value("expired"));
        mockMvc.perform(as(learner, get("/api/v1/me/courses")))
                .andExpect(jsonPath("$.data[?(@.slug == '" + PAID_COURSE + "')]").isEmpty());
    }

    @Test
    void aCardSubscriptionRenews_thenFallsBehind_thenExpires() throws Exception {
        String plan = createPlan(registerAdmin(), "9.99");
        TestUser learner = registerUser();
        giveMemberCard(learner, "20.00");

        subscribe(learner, plan, "member_card")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("active"));

        endPeriod(learner, "1 minute");
        subscriptionFacade.renewDue();
        mockMvc.perform(as(learner, get("/api/v1/me/subscription")))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.payments.length()").value(2));
        assertThat(cardBalance(learner)).isEqualByComparingTo("0.02");

        // Not enough left on the card: past due, with access through the grace period
        endPeriod(learner, "1 minute");
        subscriptionFacade.renewDue();
        mockMvc.perform(as(learner, get("/api/v1/me/subscription")))
                .andExpect(jsonPath("$.data.status").value("past_due"))
                .andExpect(jsonPath("$.data.hasAccess").value(true));
        mockMvc.perform(as(learner, post("/api/v1/me/subscription/pay"))).andExpect(status().isBadRequest());

        endPeriod(learner, "4 days");
        subscriptionService.expireDue();
        mockMvc.perform(as(learner, get("/api/v1/me/subscription")))
                .andExpect(jsonPath("$.data.status").value("expired"))
                .andExpect(jsonPath("$.data.hasAccess").value(false));
    }

    @Test
    void subscriptionRoutesAreGuarded() throws Exception {
        TestUser learner = registerUser();
        mockMvc.perform(asJson(learner, post("/api/v1/me/subscription/enrollments"), Map.of("courseSlug", PAID_COURSE)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(learner, get("/api/v1/admin/subscriptions"))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, post("/api/v1/admin/subscription-plans"),
                        Map.of("code", "nope", "name", "Nope", "price", "1.00", "billingInterval", "month")))
                .andExpect(status().isForbidden());
        subscribe(learner, "no-such-plan", "online").andExpect(status().isNotFound());
    }

    private String createPlan(TestUser admin, String price) throws Exception {
        String code = "all-access-" + System.nanoTime();
        mockMvc.perform(asJson(admin, post("/api/v1/admin/subscription-plans"),
                        Map.of("code", code, "name", "All access", "price", price, "billingInterval", "month")))
                .andExpect(status().isCreated());
        return code;
    }

    private org.springframework.test.web.servlet.ResultActions subscribe(TestUser user, String plan, String method)
            throws Exception {
        return mockMvc.perform(asJson(user, post("/api/v1/me/subscription"),
                Map.of("planCode", plan, "paymentMethod", method)));
    }

    private String currentPayment(TestUser user) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/me/subscription")))
                .andReturn().getResponse().getContentAsString();
        List<String> numbers = JsonPath.read(body, "$.data.payments[?(@.status == 'pending')].paymentNumber");
        return numbers.getFirst();
    }

    private org.springframework.test.web.servlet.ResultActions webhook(String number, String amount) throws Exception {
        String payload = number + ":PAID:" + amount + ":USD";
        return mockMvc.perform(post("/api/v1/payments/webhooks/fake")
                .contentType(MediaType.TEXT_PLAIN)
                .header("X-Payment-Signature", gateway.sign(payload))
                .content(payload));
    }

    private void endPeriod(TestUser user, String ago) {
        jdbcTemplate.update("""
                update subscriptions set current_period_end = now() - cast(? as interval)
                where student_id = (select s.id from students s join users u on u.id = s.user_id where u.email = ?)
                  and status in ('ACTIVE', 'PAST_DUE')
                """, ago, user.email());
    }

    private void giveMemberCard(TestUser user, String balance) throws Exception {
        // Enrolling creates the learner record a card belongs to
        enroll(user, FREE_COURSE);
        Integer studentId = jdbcTemplate.queryForObject(
                "select s.id from students s join users u on u.id = s.user_id where u.email = ?", Integer.class, user.email());
        mockMvc.perform(asJson(registerAdmin(), post("/api/v1/admin/member-cards"),
                        Map.of("studentId", studentId, "holderName", "Jane Learner", "openingBalance", balance)))
                .andExpect(status().isCreated());
    }

    private BigDecimal cardBalance(TestUser user) {
        return jdbcTemplate.queryForObject("""
                select c.balance from member_cards c join students s on s.id = c.student_id
                join users u on u.id = s.user_id where u.email = ?
                """, BigDecimal.class, user.email());
    }
}
