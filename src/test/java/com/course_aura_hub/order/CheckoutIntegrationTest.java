package com.course_aura_hub.order;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.enrollment.repository.EnrollmentRepository;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.order.payment.FakePaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret"
})
class CheckoutIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String WEBHOOK = "/api/v1/payments/webhooks/fake";

    @Autowired
    private FakePaymentGateway gateway;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private record Checkout(String orderNumber, String amount) {
    }

    private record OrderBody(String courseSlug) {
    }

    private Checkout checkout(TestUser user) throws Exception {
        String body = mockMvc.perform(asJson(user, post("/api/v1/me/orders"), new OrderBody(PAID_COURSE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.checkoutUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return new Checkout(JsonPath.read(body, "$.data.orderNumber"), price().toPlainString());
    }

    private BigDecimal price() {
        return courseRepository.findBySlug(PAID_COURSE).map(Course::getPrice).orElseThrow();
    }

    private ResultActions webhook(String payload, String signature) throws Exception {
        return mockMvc.perform(post(WEBHOOK)
                .contentType(MediaType.TEXT_PLAIN)
                .header("X-Payment-Signature", signature)
                .content(payload));
    }

    private ResultActions paid(Checkout order) throws Exception {
        String payload = order.orderNumber() + ":PAID:" + order.amount() + ":USD";
        return webhook(payload, gateway.sign(payload));
    }

    private void expectEnrolled(TestUser user, boolean enrolled) throws Exception {
        mockMvc.perform(as(user, get("/api/v1/courses/" + PAID_COURSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(enrolled));
    }

    @Test
    void paidWebhook_enrollsLearnerOnceAndUpdatesRevenue() throws Exception {
        TestUser learner = registerUser();
        BigDecimal revenueBefore = courseRepository.findBySlug(PAID_COURSE).orElseThrow().getRevenue();
        Checkout order = checkout(learner);
        expectEnrolled(learner, false);

        paid(order).andExpect(status().isOk());
        // Providers redeliver webhooks
        paid(order).andExpect(status().isOk());

        expectEnrolled(learner, true);
        mockMvc.perform(as(learner, get("/api/v1/me/orders/" + order.orderNumber())))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paidAt").exists())
                .andExpect(jsonPath("$.data.checkoutUrl").doesNotExist());
        assertThat(courseRepository.findBySlug(PAID_COURSE).orElseThrow().getRevenue())
                .isEqualByComparingTo(revenueBefore.add(price()));
    }

    @Test
    void repeatedCheckout_reusesTheOpenOrder() throws Exception {
        TestUser learner = registerUser();
        assertThat(checkout(learner).orderNumber()).isEqualTo(checkout(learner).orderNumber());
    }

    @Test
    void forgedOrMismatchedWebhook_doesNotEnroll() throws Exception {
        TestUser learner = registerUser();
        Checkout order = checkout(learner);

        String payload = order.orderNumber() + ":PAID:" + order.amount() + ":USD";
        webhook(payload, "bad-signature").andExpect(status().isForbidden());

        String cheap = order.orderNumber() + ":PAID:0.01:USD";
        webhook(cheap, gateway.sign(cheap)).andExpect(status().isBadRequest());

        expectEnrolled(learner, false);
    }

    @Test
    void latePaymentOnCancelledOrder_stillEnrolls() throws Exception {
        TestUser learner = registerUser();
        Checkout order = checkout(learner);

        mockMvc.perform(as(learner, post("/api/v1/me/orders/" + order.orderNumber() + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        paid(order).andExpect(status().isOk());
        expectEnrolled(learner, true);
    }

    @Test
    void refund_removesEnrollmentAndCannotRepeat() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        Checkout order = checkout(learner);
        paid(order).andExpect(status().isOk());

        mockMvc.perform(as(admin, post("/api/v1/admin/orders/" + order.orderNumber() + "/refund")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
        mockMvc.perform(as(admin, post("/api/v1/admin/orders/" + order.orderNumber() + "/refund")))
                .andExpect(status().isBadRequest());

        expectEnrolled(learner, false);
        // A redelivered payment must not re-enroll after a refund
        paid(order).andExpect(status().isOk());
        expectEnrolled(learner, false);
    }

    @Test
    void orders_arePrivateToTheirOwnerAndRefundIsAdminOnly() throws Exception {
        TestUser owner = registerUser();
        TestUser stranger = registerUser();
        Checkout order = checkout(owner);

        mockMvc.perform(as(stranger, get("/api/v1/me/orders/" + order.orderNumber())))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(stranger, post("/api/v1/me/orders/" + order.orderNumber() + "/cancel")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(owner, post("/api/v1/admin/orders/" + order.orderNumber() + "/refund")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(owner, get("/api/v1/admin/orders")))
                .andExpect(status().isForbidden());
    }

    @Test
    void freeCourseAndExistingEnrollment_areRejected() throws Exception {
        TestUser learner = registerUser();
        mockMvc.perform(asJson(learner, post("/api/v1/me/orders"), new OrderBody(FREE_COURSE)))
                .andExpect(status().isBadRequest());

        paid(checkout(learner)).andExpect(status().isOk());
        mockMvc.perform(asJson(learner, post("/api/v1/me/orders"), new OrderBody(PAID_COURSE)))
                .andExpect(status().isConflict());
    }
}
