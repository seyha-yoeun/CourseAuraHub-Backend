package com.course_aura_hub.gift;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.order.payment.FakePaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret"
})
class GiftIntegrationTest extends LearnerIntegrationTestSupport {

    private static final Pattern CODE = Pattern.compile("([0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4})");

    @Autowired
    private FakePaymentGateway gateway;
    @Autowired
    private CourseRepository courseRepository;

    @Test
    void aPaidGiftEmailsACode_thatEnrollsTheRecipientOnce() throws Exception {
        TestUser buyer = registerUser();
        String recipientEmail = uniqueEmail("friend");
        String order = gift(buyer, recipientEmail);
        mockMvc.perform(as(buyer, get("/api/v1/me/gifts"))).andExpect(jsonPath("$.data[0].status").value("awaiting_payment"));

        pay(order);
        String code = codeIn(recipientEmail);
        mockMvc.perform(as(buyer, get("/api/v1/me/courses"))).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(as(buyer, get("/api/v1/me/gifts"))).andExpect(jsonPath("$.data[0].status").value("active"));

        TestUser recipient = registerUser();
        mockMvc.perform(asJson(recipient, post("/api/v1/me/access-codes/redeem"), Map.of("code", code.toLowerCase().replace("-", " "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseSlug").value(PAID_COURSE));
        mockMvc.perform(as(recipient, get("/api/v1/me/courses"))).andExpect(jsonPath("$.data[0].slug").value(PAID_COURSE));
        mockMvc.perform(asJson(registerUser(), post("/api/v1/me/access-codes/redeem"), Map.of("code", code)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(buyer, get("/api/v1/me/gifts"))).andExpect(jsonPath("$.data[0].status").value("redeemed"));

        // A redeemed gift can't be refunded, or the recipient would keep a course nobody paid for
        mockMvc.perform(as(buyer, get("/api/v1/me/orders/" + order + "/refund-eligibility")))
                .andExpect(jsonPath("$.data.canRequest").value(false));
        mockMvc.perform(asJson(buyer, post("/api/v1/me/orders/" + order + "/refund-request"), Map.of("reason", "Changed my mind")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aBuyerKeepsTheirOwnCheckout_andCannotGiftThemselvesOrAFreeCourse() throws Exception {
        TestUser buyer = registerUser();
        gift(buyer, uniqueEmail("friend"));
        mockMvc.perform(asJson(buyer, post("/api/v1/me/orders"), Map.of("courseSlug", PAID_COURSE))).andExpect(status().isCreated());

        mockMvc.perform(asJson(buyer, post("/api/v1/me/gifts"), Map.of("courseSlug", PAID_COURSE, "recipientEmail", buyer.email())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(buyer, post("/api/v1/me/gifts"), Map.of("courseSlug", FREE_COURSE, "recipientEmail", uniqueEmail("x"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refundingAnUnusedGiftCancelsItsCode() throws Exception {
        TestUser buyer = registerUser();
        String recipientEmail = uniqueEmail("friend");
        String order = gift(buyer, recipientEmail);
        pay(order);
        String code = codeIn(recipientEmail);

        mockMvc.perform(as(registerAdmin(), post("/api/v1/admin/orders/" + order + "/refund"))).andExpect(status().isOk());
        mockMvc.perform(asJson(registerUser(), post("/api/v1/me/access-codes/redeem"), Map.of("code", code)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminsIssueBatchesWithUseLimits_andRevokeCodes() throws Exception {
        TestUser admin = registerAdmin();
        String body = mockMvc.perform(asJson(admin, post("/api/v1/admin/access-codes"),
                        Map.of("courseSlug", PAID_COURSE, "quantity", 2, "maxUses", 2, "batch", "partner-" + System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        List<String> codes = JsonPath.read(body, "$.data.codes");
        String batch = JsonPath.read(body, "$.data.batch");
        assertThat(codes).hasSize(2).doesNotHaveDuplicates();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(asJson(registerUser(), post("/api/v1/me/access-codes/redeem"), Map.of("code", codes.get(0))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(asJson(registerUser(), post("/api/v1/me/access-codes/redeem"), Map.of("code", codes.get(0))))
                .andExpect(status().isBadRequest());

        String list = mockMvc.perform(as(admin, get("/api/v1/admin/access-codes").param("batch", batch)))
                .andExpect(jsonPath("$.data[0].status").value("used_up"))
                .andReturn().getResponse().getContentAsString();
        int second = JsonPath.read(list, "$.data[1].id");
        assertThat(list).doesNotContain(codes.get(1));
        mockMvc.perform(as(admin, delete("/api/v1/admin/access-codes/" + second))).andExpect(jsonPath("$.data.status").value("revoked"));
        mockMvc.perform(asJson(registerUser(), post("/api/v1/me/access-codes/redeem"), Map.of("code", codes.get(1))))
                .andExpect(status().isBadRequest());

        TestUser learner = registerUser();
        mockMvc.perform(asJson(learner, post("/api/v1/admin/access-codes"), Map.of("courseSlug", PAID_COURSE, "quantity", 1)))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, post("/api/v1/me/access-codes/redeem"), Map.of("code", "not a code")))
                .andExpect(status().isBadRequest());
    }

    private String gift(TestUser buyer, String recipient) throws Exception {
        String body = mockMvc.perform(asJson(buyer, post("/api/v1/me/gifts"),
                        Map.of("courseSlug", PAID_COURSE, "recipientEmail", recipient, "message", "Happy birthday!")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.orderNumber");
    }

    private void pay(String orderNumber) throws Exception {
        String price = courseRepository.findBySlug(PAID_COURSE).map(Course::getPrice).orElseThrow().toPlainString();
        String payload = orderNumber + ":PAID:" + price + ":USD";
        mockMvc.perform(post("/api/v1/payments/webhooks/fake")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("X-Payment-Signature", gateway.sign(payload))
                        .content(payload))
                .andExpect(status().isOk());
    }

    private String codeIn(String email) {
        String text = emailSender.latestTo(email).orElseThrow().textBody();
        assertThat(text).contains("Happy birthday!");
        Matcher matcher = CODE.matcher(text);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
