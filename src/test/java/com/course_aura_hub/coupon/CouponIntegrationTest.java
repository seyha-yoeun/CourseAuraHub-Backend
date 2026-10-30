package com.course_aura_hub.coupon;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.order.payment.FakePaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret"
})
class CouponIntegrationTest extends LearnerIntegrationTestSupport {

    @Autowired
    private FakePaymentGateway gateway;
    @Autowired
    private CourseRepository courseRepository;

    private String uniqueCode() {
        return "SAVE-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private BigDecimal price() {
        return courseRepository.findBySlug(PAID_COURSE).map(Course::getPrice).orElseThrow();
    }

    private String createCoupon(Map<String, Object> overrides) throws Exception {
        String code = uniqueCode();
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "code", code, "discountType", "PERCENT", "discountValue", 25));
        body.putAll(overrides);
        mockMvc.perform(asJson(registerAdmin(), post("/api/v1/admin/coupons"), body))
                .andExpect(status().isCreated());
        return code;
    }

    private org.springframework.test.web.servlet.ResultActions checkout(TestUser user, String code) throws Exception {
        return mockMvc.perform(asJson(user, post("/api/v1/me/orders"), Map.of("courseSlug", PAID_COURSE, "couponCode", code)));
    }

    private boolean enrolled(TestUser user) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/courses/" + PAID_COURSE)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.enrolled");
    }

    @Test
    void percentCoupon_discountsTheAmountThePaymentMustMatch() throws Exception {
        TestUser learner = registerUser();
        String code = createCoupon(Map.of());
        BigDecimal discount = price().multiply(BigDecimal.valueOf(25)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal expected = price().subtract(discount);

        mockMvc.perform(asJson(learner, post("/api/v1/me/coupons/preview"), Map.of("courseSlug", PAID_COURSE, "code", code.toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalAmount").value(expected.doubleValue()));

        String body = checkout(learner, code)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.couponCode").value(code.toUpperCase()))
                .andExpect(jsonPath("$.data.amount").value(expected.doubleValue()))
                .andExpect(jsonPath("$.data.listAmount").value(price().doubleValue()))
                .andReturn().getResponse().getContentAsString();
        String orderNumber = JsonPath.read(body, "$.data.orderNumber");

        // Paying the undiscounted price is not this order
        String wrong = orderNumber + ":PAID:" + price().toPlainString() + ":USD";
        mockMvc.perform(post("/api/v1/payments/webhooks/fake").contentType(MediaType.TEXT_PLAIN)
                        .header("X-Payment-Signature", gateway.sign(wrong)).content(wrong))
                .andExpect(status().isBadRequest());

        String right = orderNumber + ":PAID:" + expected.toPlainString() + ":USD";
        mockMvc.perform(post("/api/v1/payments/webhooks/fake").contentType(MediaType.TEXT_PLAIN)
                        .header("X-Payment-Signature", gateway.sign(right)).content(right))
                .andExpect(status().isOk());
        assertThat(enrolled(learner)).isTrue();
    }

    @Test
    void fullDiscount_enrollsWithoutProviderAndCannotBeRefunded() throws Exception {
        TestUser learner = registerUser();
        String code = createCoupon(Map.of("discountValue", 100));

        String body = checkout(learner, code)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.amount").value(0.0))
                .andReturn().getResponse().getContentAsString();

        assertThat(enrolled(learner)).isTrue();
        mockMvc.perform(as(registerAdmin(), post("/api/v1/admin/orders/" + JsonPath.read(body, "$.data.orderNumber") + "/refund")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fixedCoupon_neverGoesBelowZero() throws Exception {
        TestUser learner = registerUser();
        checkout(learner, createCoupon(Map.of("discountType", "FIXED", "discountValue", 99999)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(0.0));
    }

    @Test
    void limit_isHeldByOpenCheckoutsAndFreedByCancelling() throws Exception {
        String code = createCoupon(Map.of("maxRedemptions", 1));
        TestUser first = registerUser();

        String body = checkout(first, code).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        checkout(registerUser(), code).andExpect(status().isBadRequest());

        mockMvc.perform(as(first, post("/api/v1/me/orders/" + JsonPath.read(body, "$.data.orderNumber") + "/cancel")))
                .andExpect(status().isOk());
        checkout(registerUser(), code).andExpect(status().isCreated());
    }

    @Test
    void sameLearner_reusesOpenOrderButCannotRedeemTwice() throws Exception {
        TestUser learner = registerUser();
        String code = createCoupon(Map.of("maxRedemptions", 1));

        String first = checkout(learner, code).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = checkout(learner, code).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(first, "$.data.orderNumber");
        assertThat((String) JsonPath.read(second, "$.data.orderNumber")).isEqualTo(number);

        String freeCode = createCoupon(Map.of("discountValue", 100));
        checkout(learner, freeCode).andExpect(status().isCreated());
        // Re-buying is blocked by enrollment; the code itself must also be spent
        mockMvc.perform(asJson(learner, post("/api/v1/me/coupons/preview"), Map.of("courseSlug", PAID_COURSE, "code", freeCode)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCodes_areRejectedAndLeaveNoOrder() throws Exception {
        TestUser learner = registerUser();
        String expired = createCoupon(Map.of("expiresAt", Instant.now().minusSeconds(60).toString()));
        String future = createCoupon(Map.of("startsAt", Instant.now().plusSeconds(3600).toString()));
        String otherCourse = createCoupon(Map.of("courseSlug", FREE_COURSE));
        String inactive = createCoupon(Map.of());

        String listing = mockMvc.perform(as(registerAdmin(), get("/api/v1/admin/coupons")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.<java.util.List<Integer>>read(listing, "$.data[?(@.code == '" + inactive.toUpperCase() + "')].id").getFirst();
        mockMvc.perform(asJson(registerAdmin(), patch("/api/v1/admin/coupons/" + id), Map.of("active", false)))
                .andExpect(status().isOk());

        for (String code : new String[] {"NOPE-123", expired, future, otherCourse, inactive}) {
            checkout(learner, code)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("This coupon code is not valid"));
        }
        mockMvc.perform(as(learner, get("/api/v1/me/orders"))).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void admin_validatesCouponsAndGuardsTheEndpoints() throws Exception {
        TestUser admin = registerAdmin();
        mockMvc.perform(asJson(admin, post("/api/v1/admin/coupons"), Map.of("code", uniqueCode(), "discountType", "PERCENT", "discountValue", 101)))
                .andExpect(status().isBadRequest());

        String code = createCoupon(Map.of());
        mockMvc.perform(asJson(admin, post("/api/v1/admin/coupons"), Map.of("code", code.toLowerCase(), "discountType", "FIXED", "discountValue", 5)))
                .andExpect(status().isConflict());

        TestUser learner = registerUser();
        mockMvc.perform(as(learner, get("/api/v1/admin/coupons"))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, post("/api/v1/admin/coupons"), Map.of("code", uniqueCode(), "discountType", "FIXED", "discountValue", 5)))
                .andExpect(status().isForbidden());
    }
}
