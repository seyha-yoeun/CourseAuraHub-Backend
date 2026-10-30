package com.course_aura_hub.course;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseDiscountRepository;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payment.provider=fake",
        "app.payment.webhook-secret=test-webhook-secret"
})
class CourseDiscountIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String DISCOUNTS = "/api/v1/admin/discounts";

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseDiscountRepository discountRepository;

    @AfterEach
    void clearDiscounts() {
        discountRepository.deleteAll();
    }

    private Course course() {
        return courseRepository.findBySlug(PAID_COURSE).orElseThrow();
    }

    private BigDecimal percentOff(int percent) {
        BigDecimal price = course().getPrice();
        BigDecimal off = price.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return price.subtract(off);
    }

    private Map<String, Object> percent(int value) {
        Map<String, Object> body = new HashMap<>();
        body.put("courseSlug", PAID_COURSE);
        body.put("discountType", "PERCENT");
        body.put("discountValue", value);
        return body;
    }

    private Integer createDiscount(TestUser admin, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(asJson(admin, post(DISCOUNTS), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    @Test
    void onlyAdminsCanManageDiscounts() throws Exception {
        TestUser learner = registerUser();

        mockMvc.perform(get(DISCOUNTS)).andExpect(status().isUnauthorized());
        mockMvc.perform(as(learner, get(DISCOUNTS))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, post(DISCOUNTS), percent(50))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, patch(DISCOUNTS + "/1"), Map.of("active", false))).andExpect(status().isForbidden());
        mockMvc.perform(as(learner, delete(DISCOUNTS + "/1"))).andExpect(status().isForbidden());
    }

    @Test
    void runningSale_lowersThePublicPriceAndKeepsTheRegularPriceStored() throws Exception {
        BigDecimal regular = course().getPrice();
        BigDecimal salePrice = percentOff(20);
        createDiscount(registerAdmin(), percent(20));

        mockMvc.perform(get("/api/v1/courses/" + PAID_COURSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(salePrice.doubleValue()))
                .andExpect(jsonPath("$.data.originalPrice").value(regular.max(course().getOriginalPrice()).doubleValue()))
                .andExpect(jsonPath("$.data.sale.salePrice").value(salePrice.doubleValue()))
                .andExpect(jsonPath("$.data.sale.discountType").value("PERCENT"));

        mockMvc.perform(get("/api/v1/courses").param("search", course().getTitle()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].price").value(salePrice.doubleValue()));

        assertThat(course().getPrice()).isEqualByComparingTo(regular);
    }

    @Test
    void adminEditDuringASale_doesNotSaveTheSalePriceAsTheRegularPrice() throws Exception {
        TestUser admin = registerAdmin();
        BigDecimal regular = course().getPrice();
        createDiscount(admin, percent(50));

        String body = mockMvc.perform(as(admin, get("/api/v1/courses/" + PAID_COURSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(regular.doubleValue()))
                .andExpect(jsonPath("$.data.sale.salePrice").value(percentOff(50).doubleValue()))
                .andReturn().getResponse().getContentAsString();

        // What the edit form does: send the loaded price straight back
        Number loadedPrice = JsonPath.read(body, "$.data.price");
        mockMvc.perform(asJson(admin, put("/api/v1/courses/" + PAID_COURSE), Map.of("price", loadedPrice)))
                .andExpect(status().isOk());

        assertThat(course().getPrice()).isEqualByComparingTo(regular);
    }

    @Test
    void saleOutsideItsWindowOrSwitchedOff_changesNothing() throws Exception {
        TestUser admin = registerAdmin();
        BigDecimal regular = course().getPrice();

        Map<String, Object> scheduled = percent(30);
        scheduled.put("startsAt", Instant.now().plus(Duration.ofDays(7)));
        Integer upcoming = createDiscount(admin, scheduled);
        mockMvc.perform(get("/api/v1/courses/" + PAID_COURSE))
                .andExpect(jsonPath("$.data.price").value(regular.doubleValue()))
                .andExpect(jsonPath("$.data.sale").doesNotExist());

        // Started and then ended in the past
        mockMvc.perform(asJson(admin, patch(DISCOUNTS + "/" + upcoming), Map.of(
                        "startsAt", Instant.now().minus(Duration.ofDays(2)),
                        "endsAt", Instant.now().minus(Duration.ofDays(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.running").value(false));
        mockMvc.perform(get("/api/v1/courses/" + PAID_COURSE))
                .andExpect(jsonPath("$.data.price").value(regular.doubleValue()));

        Integer running = createDiscount(admin, percent(30));
        mockMvc.perform(asJson(admin, patch(DISCOUNTS + "/" + running), Map.of("active", false)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses/" + PAID_COURSE))
                .andExpect(jsonPath("$.data.price").value(regular.doubleValue()));
    }

    @Test
    void checkout_chargesTheSalePriceAndACouponStacksOnIt() throws Exception {
        TestUser admin = registerAdmin();
        BigDecimal regular = course().getPrice();
        BigDecimal salePrice = percentOff(20);
        createDiscount(admin, percent(20));

        mockMvc.perform(asJson(registerUser(), post("/api/v1/me/orders"), Map.of("courseSlug", PAID_COURSE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(salePrice.doubleValue()))
                .andExpect(jsonPath("$.data.listAmount").value(regular.doubleValue()))
                .andExpect(jsonPath("$.data.discountAmount").value(regular.subtract(salePrice).doubleValue()));

        String code = "SALE" + System.nanoTime();
        mockMvc.perform(asJson(admin, post("/api/v1/admin/coupons"), Map.of(
                        "code", code, "discountType", "FIXED", "discountValue", 5)))
                .andExpect(status().isCreated());
        BigDecimal expected = salePrice.subtract(BigDecimal.valueOf(5));

        mockMvc.perform(asJson(registerUser(), post("/api/v1/me/orders"), Map.of("courseSlug", PAID_COURSE, "couponCode", code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(expected.doubleValue()))
                .andExpect(jsonPath("$.data.listAmount").value(regular.doubleValue()))
                .andExpect(jsonPath("$.data.discountAmount").value(regular.subtract(expected).doubleValue()));
    }

    @Test
    void endingASale_replacesAnUnpaidOrderWithTheNewPrice() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        Integer id = createDiscount(admin, percent(20));

        mockMvc.perform(asJson(learner, post("/api/v1/me/orders"), Map.of("courseSlug", PAID_COURSE)))
                .andExpect(jsonPath("$.data.amount").value(percentOff(20).doubleValue()));

        mockMvc.perform(as(admin, delete(DISCOUNTS + "/" + id))).andExpect(status().isNoContent());

        mockMvc.perform(asJson(learner, post("/api/v1/me/orders"), Map.of("courseSlug", PAID_COURSE)))
                .andExpect(jsonPath("$.data.amount").value(course().getPrice().doubleValue()))
                .andExpect(jsonPath("$.data.discountAmount").value(0.0));
    }

    @Test
    void fullDiscount_makesTheCourseFreeToEnroll() throws Exception {
        TestUser learner = registerUser();
        createDiscount(registerAdmin(), percent(100));

        mockMvc.perform(asJson(learner, post("/api/v1/me/orders"), Map.of("courseSlug", PAID_COURSE)))
                .andExpect(status().isBadRequest());
        enroll(learner, PAID_COURSE);
    }

    @Test
    void invalidDiscounts_areRejected() throws Exception {
        TestUser admin = registerAdmin();

        mockMvc.perform(asJson(admin, post(DISCOUNTS), percent(101))).andExpect(status().isBadRequest());

        Map<String, Object> backwards = percent(10);
        backwards.put("startsAt", Instant.now().plus(Duration.ofDays(2)));
        backwards.put("endsAt", Instant.now().plus(Duration.ofDays(1)));
        mockMvc.perform(asJson(admin, post(DISCOUNTS), backwards)).andExpect(status().isBadRequest());

        Map<String, Object> free = percent(10);
        free.put("courseSlug", FREE_COURSE);
        mockMvc.perform(asJson(admin, post(DISCOUNTS), free)).andExpect(status().isBadRequest());

        Map<String, Object> unknown = percent(10);
        unknown.put("courseSlug", "no-such-course");
        mockMvc.perform(asJson(admin, post(DISCOUNTS), unknown)).andExpect(status().isNotFound());
    }

    @Test
    void overlappingSales_areRejectedButBackToBackOnesAreAllowed() throws Exception {
        TestUser admin = registerAdmin();
        Instant end = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> first = percent(10);
        first.put("endsAt", end);
        Integer firstId = createDiscount(admin, first);

        Map<String, Object> overlapping = percent(15);
        overlapping.put("startsAt", end.minus(Duration.ofDays(1)));
        mockMvc.perform(asJson(admin, post(DISCOUNTS), overlapping)).andExpect(status().isConflict());

        Map<String, Object> next = percent(15);
        next.put("startsAt", end);
        createDiscount(admin, next);

        // Stretching the first sale into the next one is the same conflict
        mockMvc.perform(asJson(admin, patch(DISCOUNTS + "/" + firstId), Map.of("endsAt", end.plus(Duration.ofDays(1)))))
                .andExpect(status().isConflict());

        mockMvc.perform(as(admin, get(DISCOUNTS).param("courseSlug", PAID_COURSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
