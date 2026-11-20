package com.course_aura_hub.referral;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.order.payment.FakePaymentGateway;
import com.course_aura_hub.referral.service.ReferralService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret",
        "app.referrals.reward-amount=5.00"
})
class ReferralIntegrationTest extends LearnerIntegrationTestSupport {

    @Autowired
    private FakePaymentGateway gateway;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private ReferralService referralService;

    @Test
    void aReferrerIsCredited_afterTheFriendsFirstPurchaseClearsTheHold() throws Exception {
        TestUser referrer = registerUser();
        giveMemberCard(referrer);
        String code = code(referrer);

        TestUser friend = registerUser();
        claim(friend, code).andExpect(status().isCreated()).andExpect(jsonPath("$.data.referrerFirstName").value("Jane"));
        claim(friend, code).andExpect(status().isConflict());

        String order = buy(friend);
        mockMvc.perform(as(referrer, get("/api/v1/me/referral")))
                .andExpect(jsonPath("$.data.qualified").value(1))
                .andExpect(jsonPath("$.data.friends[0].firstName").value("Jane"))
                .andExpect(jsonPath("$..email").doesNotExist());

        // Not yet past the hold
        assertThat(referralService.rewardDue()).isZero();
        jdbcTemplate.update("update referrals set qualified_at = now() - interval '15 days' where order_id = "
                + "(select id from orders where order_number = ?)", order);
        assertThat(referralService.rewardDue()).isEqualTo(1);
        assertThat(referralService.rewardDue()).isZero();

        mockMvc.perform(as(referrer, get("/api/v1/me/referral")))
                .andExpect(jsonPath("$.data.rewarded").value(1))
                .andExpect(jsonPath("$.data.earned").value(5.0));
        BigDecimal balance = jdbcTemplate.queryForObject("""
                select c.balance from member_cards c join students s on s.id = c.student_id
                join users u on u.id = s.user_id where u.email = ?
                """, BigDecimal.class, referrer.email());
        assertThat(balance).isEqualByComparingTo("5.00");
    }

    @Test
    void aRefundBeforeTheReward_undoesTheQualification() throws Exception {
        TestUser referrer = registerUser();
        TestUser friend = registerUser();
        claim(friend, code(referrer)).andExpect(status().isCreated());
        String order = buy(friend);

        mockMvc.perform(as(registerAdmin(), post("/api/v1/admin/orders/" + order + "/refund"))).andExpect(status().isOk());

        mockMvc.perform(as(referrer, get("/api/v1/me/referral")))
                .andExpect(jsonPath("$.data.signedUp").value(1))
                .andExpect(jsonPath("$.data.qualified").value(0));
    }

    @Test
    void codesCantBeUsedOnYourself_afterBuying_orWhenUnknown() throws Exception {
        TestUser user = registerUser();
        claim(user, code(user)).andExpect(status().isBadRequest());
        claim(user, "NOSUCHCODE").andExpect(status().isNotFound());

        TestUser buyer = registerUser();
        buy(buyer);
        claim(buyer, code(registerUser())).andExpect(status().isBadRequest());

        mockMvc.perform(as(user, get("/api/v1/admin/referrals"))).andExpect(status().isForbidden());
        mockMvc.perform(as(registerAdmin(), get("/api/v1/admin/referrals"))).andExpect(status().isOk());
    }

    private String code(TestUser user) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/me/referral")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.data.code");
        assertThat((String) JsonPath.read(body, "$.data.link")).endsWith("?ref=" + code);
        return code;
    }

    private org.springframework.test.web.servlet.ResultActions claim(TestUser user, String code) throws Exception {
        return mockMvc.perform(asJson(user, post("/api/v1/me/referral/claim"), Map.of("code", code.toLowerCase())));
    }

    private void giveMemberCard(TestUser user) throws Exception {
        // Enrolling creates the learner record a card belongs to
        enroll(user, FREE_COURSE);
        Integer studentId = jdbcTemplate.queryForObject(
                "select s.id from students s join users u on u.id = s.user_id where u.email = ?", Integer.class, user.email());
        mockMvc.perform(asJson(registerAdmin(), post("/api/v1/admin/member-cards"),
                        Map.of("studentId", studentId, "holderName", "Jane Learner")))
                .andExpect(status().isCreated());
    }

    private String buy(TestUser user) throws Exception {
        String body = mockMvc.perform(asJson(user, post("/api/v1/me/orders"), Map.of("courseSlug", PAID_COURSE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderNumber = JsonPath.read(body, "$.data.orderNumber");
        String price = courseRepository.findBySlug(PAID_COURSE).map(Course::getPrice).orElseThrow().toPlainString();
        String payload = orderNumber + ":PAID:" + price + ":USD";
        mockMvc.perform(post("/api/v1/payments/webhooks/fake")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("X-Payment-Signature", gateway.sign(payload))
                        .content(payload))
                .andExpect(status().isOk());
        return orderNumber;
    }
}
