package com.course_aura_hub.refund;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret"
})
class RefundRequestIntegrationTest extends LearnerIntegrationTestSupport {

    @Autowired
    private FakePaymentGateway gateway;
    @Autowired
    private CourseRepository courseRepository;

    @Test
    void aRecentPurchase_isRefundedAutomatically() throws Exception {
        TestUser learner = registerUser();
        String order = paidOrder(learner);

        mockMvc.perform(as(learner, get(path(order) + "/refund-eligibility")))
                .andExpect(jsonPath("$.data.canRequest").value(true))
                .andExpect(jsonPath("$.data.automatic").value(true));
        request(learner, order)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("approved"))
                .andExpect(jsonPath("$.data.autoApproved").value(true));

        mockMvc.perform(as(learner, get("/api/v1/me/orders/" + order))).andExpect(jsonPath("$.data.status").value("REFUNDED"));
        mockMvc.perform(as(learner, get("/api/v1/me/courses"))).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void anOldPurchase_waitsForAnAdmin_whoApprovesIt() throws Exception {
        TestUser learner = registerUser();
        String order = paidOrder(learner);
        jdbcTemplate.update("update orders set paid_at = now() - interval '30 days' where order_number = ?", order);

        String body = request(learner, order)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.id");
        request(learner, order).andExpect(status().isConflict());

        TestUser admin = registerAdmin();
        mockMvc.perform(as(admin, get("/api/v1/admin/refund-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.request.id == " + id + ")].studentEmail").value(learner.email()));
        mockMvc.perform(as(admin, post("/api/v1/admin/refund-requests/" + id + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"))
                .andExpect(jsonPath("$.data.autoApproved").value(false));
        mockMvc.perform(as(learner, get("/api/v1/me/orders/" + order))).andExpect(jsonPath("$.data.status").value("REFUNDED"));
        mockMvc.perform(as(admin, post("/api/v1/admin/refund-requests/" + id + "/approve"))).andExpect(status().isBadRequest());
    }

    @Test
    void anAdminCanDecline_withANote_andTheLearnerCannotAskAgain() throws Exception {
        TestUser learner = registerUser();
        String order = paidOrder(learner);
        completeLessons(learner, 3);

        String body = request(learner, order)
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.progressPercent").value(50))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.id");

        TestUser admin = registerAdmin();
        mockMvc.perform(asJson(admin, post("/api/v1/admin/refund-requests/" + id + "/reject"), Map.of("note", " ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(admin, post("/api/v1/admin/refund-requests/" + id + "/reject"),
                        Map.of("note", "You completed half the course.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("rejected"));

        mockMvc.perform(as(learner, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].type").value("refund_request"));
        mockMvc.perform(as(learner, get(path(order) + "/refund-eligibility")))
                .andExpect(jsonPath("$.data.canRequest").value(false));
        request(learner, order).andExpect(status().isBadRequest());
        mockMvc.perform(as(learner, get("/api/v1/me/orders/" + order))).andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Test
    void otherPeoplesOrdersAndTheAdminQueueAreOffLimits() throws Exception {
        TestUser owner = registerUser();
        String order = paidOrder(owner);
        TestUser stranger = registerUser();

        request(stranger, order).andExpect(status().isNotFound());
        mockMvc.perform(as(stranger, get("/api/v1/admin/refund-requests"))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(owner, post(path(order) + "/refund-request"), Map.of("reason", "")))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions request(TestUser learner, String order) throws Exception {
        return mockMvc.perform(asJson(learner, post(path(order) + "/refund-request"), Map.of("reason", "Not what I expected")));
    }

    private static String path(String order) {
        return "/api/v1/me/orders/" + order;
    }

    private void completeLessons(TestUser learner, int count) throws Exception {
        List<Integer> lessons = jdbcTemplate.queryForList("""
                select l.id from lessons l join course_sections s on s.id = l.section_id join courses c on c.id = s.course_id
                where c.slug = ? order by s.position, l.position limit ?
                """, Integer.class, PAID_COURSE, count);
        for (Integer lesson : lessons) {
            mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lesson + "/completion"))).andExpect(status().isOk());
        }
    }

    private String paidOrder(TestUser user) throws Exception {
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
