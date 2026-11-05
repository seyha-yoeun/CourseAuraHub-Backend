package com.course_aura_hub.order;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.order.payment.FakePaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret"
})
class ReceiptIntegrationTest extends LearnerIntegrationTestSupport {

    @Autowired
    private FakePaymentGateway gateway;
    @Autowired
    private CourseRepository courseRepository;

    private record OrderBody(String courseSlug) {
    }

    private String checkout(TestUser user) throws Exception {
        String body = mockMvc.perform(asJson(user, post("/api/v1/me/orders"), new OrderBody(PAID_COURSE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.orderNumber");
    }

    private String paidOrder(TestUser user) throws Exception {
        String orderNumber = checkout(user);
        String price = courseRepository.findBySlug(PAID_COURSE).map(Course::getPrice).orElseThrow().toPlainString();
        String payload = orderNumber + ":PAID:" + price + ":USD";
        mockMvc.perform(post("/api/v1/payments/webhooks/fake")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("X-Payment-Signature", gateway.sign(payload))
                        .content(payload))
                .andExpect(status().isOk());
        return orderNumber;
    }

    private static String receipt(String orderNumber) {
        return "/api/v1/me/orders/" + orderNumber + "/receipt";
    }

    private static String adminReceipt(String orderNumber) {
        return "/api/v1/admin/orders/" + orderNumber + "/receipt";
    }

    @Test
    void owner_downloadsPdfForPaidOrder() throws Exception {
        TestUser learner = registerUser();
        String orderNumber = paidOrder(learner);

        byte[] pdf = mockMvc.perform(as(learner, get(receipt(orderNumber))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("receipt-" + orderNumber.toUpperCase())))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void refundedOrder_stillHasReceipt() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        String orderNumber = paidOrder(learner);
        mockMvc.perform(as(admin, post("/api/v1/admin/orders/" + orderNumber + "/refund")))
                .andExpect(status().isOk());

        mockMvc.perform(as(learner, get(receipt(orderNumber))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void pendingOrder_hasNoReceipt() throws Exception {
        TestUser learner = registerUser();
        String orderNumber = checkout(learner);

        mockMvc.perform(as(learner, get(receipt(orderNumber))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherLearnersOrder_isNotFound() throws Exception {
        String orderNumber = paidOrder(registerUser());
        TestUser stranger = registerUser();

        mockMvc.perform(as(stranger, get(receipt(orderNumber))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(receipt(orderNumber)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminReceipt_isAdminOnly() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        String orderNumber = paidOrder(learner);

        mockMvc.perform(as(admin, get(adminReceipt(orderNumber))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mockMvc.perform(as(learner, get(adminReceipt(orderNumber))))
                .andExpect(status().isForbidden());
    }
}
