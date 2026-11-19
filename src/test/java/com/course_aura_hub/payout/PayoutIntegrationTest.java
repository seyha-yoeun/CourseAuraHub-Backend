package com.course_aura_hub.payout;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.order.payment.FakePaymentGateway;
import com.course_aura_hub.payout.service.EarningService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret",
        "app.payouts.instructor-share-percent=70",
        "app.payouts.minimum-amount=10.00"
})
class PayoutIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String EARNINGS = "/api/v1/instructor/earnings";
    private static final String PAYOUTS = "/api/v1/instructor/payouts";

    @Autowired
    private FakePaymentGateway gateway;
    @Autowired
    private EarningService earningService;

    @Test
    void aSaleEarnsTheOwner_afterTheHold_andIsPaidOutThroughAdminReview() throws Exception {
        TestUser admin = registerAdmin();
        TestUser instructor = registerInstructor();
        String slug = createOwnedCourse(admin, instructor, "100.00");
        String order = buy(registerUser(), slug, "100.00");

        mockMvc.perform(as(instructor, get(EARNINGS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].orderNumber").value(order))
                .andExpect(jsonPath("$.data.items[0].status").value("pending"))
                .andExpect(jsonPath("$.data.items[0].net").value(70.0))
                .andExpect(jsonPath("$.data.items[0].platformFee").value(30.0))
                .andExpect(jsonPath("$.data.summary.pending").value(70.0))
                .andExpect(jsonPath("$.data.summary.canRequest").value(false))
                .andExpect(jsonPath("$..email").doesNotExist());
        requestPayout(instructor).andExpect(status().isBadRequest());

        releaseAll();
        mockMvc.perform(as(instructor, get(EARNINGS)))
                .andExpect(jsonPath("$.data.summary.available").value(70.0))
                .andExpect(jsonPath("$.data.summary.canRequest").value(true));

        String body = requestPayout(instructor)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(70.0))
                .andExpect(jsonPath("$.data.status").value("requested"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.id");
        requestPayout(instructor).andExpect(status().isConflict());
        mockMvc.perform(as(instructor, get(EARNINGS)))
                .andExpect(jsonPath("$.data.summary.available").value(0.0))
                .andExpect(jsonPath("$.data.summary.inPayout").value(70.0));

        mockMvc.perform(as(admin, get("/api/v1/admin/payouts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.payout.id == " + id + ")].instructorEmail").value(instructor.email()));
        mockMvc.perform(as(admin, post("/api/v1/admin/payouts/" + id + "/mark-paid"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, post("/api/v1/admin/payouts/" + id + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"));
        mockMvc.perform(asJson(admin, post("/api/v1/admin/payouts/" + id + "/mark-paid"),
                        Map.of("paymentReference", "BANK-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("paid"))
                .andExpect(jsonPath("$.data.paymentReference").value("BANK-123"));

        mockMvc.perform(as(instructor, get(EARNINGS)))
                .andExpect(jsonPath("$.data.items[0].status").value("paid"))
                .andExpect(jsonPath("$.data.summary.paidOut").value(70.0));
        mockMvc.perform(as(instructor, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].type").value("payout"));

        // A refund after the payout comes off the next one
        mockMvc.perform(as(admin, post("/api/v1/admin/orders/" + order + "/refund"))).andExpect(status().isOk());
        mockMvc.perform(as(instructor, get(EARNINGS)))
                .andExpect(jsonPath("$.data.items[0].type").value("refund"))
                .andExpect(jsonPath("$.data.items[0].net").value(-70.0))
                .andExpect(jsonPath("$.data.summary.available").value(-70.0))
                .andExpect(jsonPath("$.data.summary.canRequest").value(false));
    }

    @Test
    void aRefundInsideTheHold_reversesTheSale() throws Exception {
        TestUser admin = registerAdmin();
        TestUser instructor = registerInstructor();
        String order = buy(registerUser(), createOwnedCourse(admin, instructor, "50.00"), "50.00");

        mockMvc.perform(as(admin, post("/api/v1/admin/orders/" + order + "/refund"))).andExpect(status().isOk());
        releaseAll();

        mockMvc.perform(as(instructor, get(EARNINGS)))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("reversed"))
                .andExpect(jsonPath("$.data.summary.available").value(0.0));
    }

    @Test
    void aDeclinedPayout_releasesItsEarnings_andTheCourseShareCanBeChanged() throws Exception {
        TestUser admin = registerAdmin();
        TestUser instructor = registerInstructor();
        String slug = createOwnedCourse(admin, instructor, "40.00");

        Map<String, Object> half = new HashMap<>();
        half.put("instructorPercent", 50);
        mockMvc.perform(asJson(admin, put("/api/v1/admin/courses/" + slug + "/revenue-share"), half))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.custom").value(true));
        buy(registerUser(), slug, "40.00");
        releaseAll();

        String body = requestPayout(instructor)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(20.0))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.id");

        mockMvc.perform(asJson(admin, post("/api/v1/admin/payouts/" + id + "/reject"), Map.of("note", " ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(admin, post("/api/v1/admin/payouts/" + id + "/reject"), Map.of("note", "Wrong account")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("rejected"));
        mockMvc.perform(as(instructor, get(EARNINGS)))
                .andExpect(jsonPath("$.data.summary.available").value(20.0))
                .andExpect(jsonPath("$.data.summary.inPayout").value(0.0));

        Map<String, Object> reset = new HashMap<>();
        reset.put("instructorPercent", null);
        mockMvc.perform(asJson(admin, put("/api/v1/admin/courses/" + slug + "/revenue-share"), reset))
                .andExpect(jsonPath("$.data.custom").value(false))
                .andExpect(jsonPath("$.data.instructorPercent").value(70.0));
    }

    @Test
    void adminCoursesEarnNothing_andTheRoutesAreGuarded() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        String slug = createCourse(admin, "30.00");
        buy(learner, slug, "30.00");
        Integer earnings = jdbcTemplate.queryForObject("""
                select count(*) from instructor_earnings e join courses c on c.id = e.course_id where c.slug = ?
                """, Integer.class, slug);
        org.assertj.core.api.Assertions.assertThat(earnings).isZero();

        mockMvc.perform(as(learner, get(EARNINGS))).andExpect(status().isForbidden());
        mockMvc.perform(as(registerInstructor(), get("/api/v1/admin/payouts"))).andExpect(status().isForbidden());
        mockMvc.perform(get(EARNINGS)).andExpect(status().isUnauthorized());
    }

    private ResultActions requestPayout(TestUser instructor) throws Exception {
        return mockMvc.perform(asJson(instructor, post(PAYOUTS),
                Map.of("method", "bank", "accountDetails", "ABA 000 111 222")));
    }

    private void releaseAll() {
        jdbcTemplate.update("update instructor_earnings set available_at = now() - interval '1 minute' where status = 'PENDING'");
        earningService.releaseDue();
    }

    private TestUser registerInstructor() throws Exception {
        TestUser user = registerUser();
        userRepository.findByEmail(user.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(entity);
        });
        return user;
    }

    private String createOwnedCourse(TestUser admin, TestUser owner, String price) throws Exception {
        String slug = createCourse(admin, price);
        jdbcTemplate.update("update courses set owner_id = ? where slug = ?", userId(owner.email()), slug);
        return slug;
    }

    private String createCourse(TestUser admin, String price) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Payout " + System.nanoTime(), "status", "published", "price", price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }

    private String buy(TestUser learner, String slug, String price) throws Exception {
        String body = mockMvc.perform(asJson(learner, post("/api/v1/me/orders"), Map.of("courseSlug", slug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderNumber = JsonPath.read(body, "$.data.orderNumber");
        String payload = orderNumber + ":PAID:" + price + ":USD";
        mockMvc.perform(post("/api/v1/payments/webhooks/fake")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("X-Payment-Signature", gateway.sign(payload))
                        .content(payload))
                .andExpect(status().isOk());
        return orderNumber;
    }
}
