package com.course_aura_hub.notification;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.course_aura_hub.notification.service.NotificationStreamRegistry;
import com.course_aura_hub.order.payment.FakePaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
class NotificationIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String NOTIFICATIONS = "/api/v1/me/notifications";
    /** Seeded by V0013 without an account. */
    private static final int STUDENT_WITHOUT_ACCOUNT = 11;

    @MockitoSpyBean
    private FakePaymentGateway gateway;
    @MockitoSpyBean
    private NotificationStreamRegistry streamRegistry;
    @Autowired
    private CourseRepository courseRepository;

    private record OrderBody(String courseSlug) {
    }

    private String feed(TestUser user) throws Exception {
        return mockMvc.perform(as(user, get(NOTIFICATIONS).param("size", "50")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> types(TestUser user) throws Exception {
        return JsonPath.read(feed(user), "$.data.items[*].type");
    }

    private Integer newestId(TestUser user) throws Exception {
        return JsonPath.read(feed(user), "$.data.items[0].id");
    }

    private String createCourse(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"), Map.of(
                        "title", "Notification Test " + UUID.randomUUID(),
                        "price", 0,
                        "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }

    private Integer createSection(TestUser admin, String slug) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Intro")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private void addLesson(TestUser admin, String slug, Integer sectionId, String title) throws Exception {
        mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", title, "type", "video", "durationMinutes", 5)))
                .andExpect(status().isCreated());
    }

    private String checkout(TestUser user) throws Exception {
        String body = mockMvc.perform(asJson(user, post("/api/v1/me/orders"), new OrderBody(PAID_COURSE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.orderNumber");
    }

    private ResultActions paymentEvent(String orderNumber, String outcome) throws Exception {
        String amount = courseRepository.findBySlug(PAID_COURSE).map(Course::getPrice).orElseThrow().toPlainString();
        String payload = orderNumber + ":" + outcome + ":" + amount + ":USD";
        return mockMvc.perform(post("/api/v1/payments/webhooks/fake")
                .contentType(MediaType.TEXT_PLAIN)
                .header("X-Payment-Signature", gateway.sign(payload))
                .content(payload));
    }

    @Test
    void feed_pagesFiltersAndTracksRead() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        enroll(learner, createCourse(admin));

        mockMvc.perform(as(learner, get(NOTIFICATIONS).param("size", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].type").value("enrolled"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].link").isNotEmpty());
        mockMvc.perform(as(learner, get(NOTIFICATIONS + "/unread-count")))
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        mockMvc.perform(as(learner, patch(NOTIFICATIONS + "/" + newestId(learner) + "/read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(as(learner, get(NOTIFICATIONS).param("unreadOnly", "true")))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(as(learner, post(NOTIFICATIONS + "/read-all")))
                .andExpect(status().isOk());
        mockMvc.perform(as(learner, get(NOTIFICATIONS + "/unread-count")))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(as(learner, delete(NOTIFICATIONS + "/" + newestId(learner))))
                .andExpect(status().isOk());
        assertThat(types(learner)).hasSize(1);
    }

    @Test
    void notifications_arePrivate() throws Exception {
        TestUser owner = registerUser();
        TestUser other = registerUser();
        enroll(owner, FREE_COURSE);
        Integer id = newestId(owner);

        mockMvc.perform(get(NOTIFICATIONS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(NOTIFICATIONS + "/stream")).andExpect(status().isUnauthorized());

        mockMvc.perform(as(other, patch(NOTIFICATIONS + "/" + id + "/read"))).andExpect(status().isNotFound());
        mockMvc.perform(as(other, delete(NOTIFICATIONS + "/" + id))).andExpect(status().isNotFound());
        assertThat(types(other)).isEmpty();

        mockMvc.perform(as(owner, get(NOTIFICATIONS + "/unread-count")))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void paidOrder_notifiesOnceDespiteRepeatedWebhook() throws Exception {
        TestUser learner = registerUser();
        String order = checkout(learner);

        paymentEvent(order, "PAID").andExpect(status().isOk());
        paymentEvent(order, "PAID").andExpect(status().isOk());

        assertThat(types(learner)).containsExactly("enrolled");
    }

    @Test
    void failedPayment_notifiesOnlyWhileOrderIsPending() throws Exception {
        TestUser learner = registerUser();
        String order = checkout(learner);

        paymentEvent(order, "FAILED").andExpect(status().isOk());
        paymentEvent(order, "FAILED").andExpect(status().isOk());

        assertThat(types(learner)).containsExactly("payment_failed");
    }

    @Test
    void refund_notifiesLearner() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        String order = checkout(learner);
        paymentEvent(order, "PAID").andExpect(status().isOk());

        mockMvc.perform(as(admin, post("/api/v1/admin/orders/" + order + "/refund")))
                .andExpect(status().isOk());

        assertThat(types(learner)).containsExactly("order_refunded", "enrolled");
    }

    @Test
    void failedRefund_rollsBackTheNotificationAndPushesNothing() throws Exception {
        TestUser learner = registerUser();
        TestUser admin = registerAdmin();
        String order = checkout(learner);
        paymentEvent(order, "PAID").andExpect(status().isOk());
        doThrow(new IllegalStateException("provider down")).when(gateway).refund(any());
        Mockito.clearInvocations(streamRegistry);

        mockMvc.perform(as(admin, post("/api/v1/admin/orders/" + order + "/refund")))
                .andExpect(status().is5xxServerError());

        assertThat(types(learner)).containsExactly("enrolled");
        Mockito.verify(streamRegistry, Mockito.after(500).never())
                .send(any(UUID.class), any(), anyLong());
    }

    @Test
    void certificate_notifiesOnceWithItsLink() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        String curriculum = mockMvc.perform(as(learner, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andReturn().getResponse().getContentAsString();
        List<Integer> lessonIds = JsonPath.read(curriculum, "$.data.sections[*].lessons[*].id");

        for (Integer lessonId : lessonIds) {
            mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonId + "/completion")))
                    .andExpect(status().isOk());
        }
        // Finishing the course again must not announce a second certificate
        mockMvc.perform(as(learner, delete("/api/v1/me/lessons/" + lessonIds.getLast() + "/completion")))
                .andExpect(status().isOk());
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonIds.getLast() + "/completion")))
                .andExpect(status().isOk());

        assertThat(types(learner)).containsExactly("certificate_issued", "enrolled");
        String link = JsonPath.read(feed(learner), "$.data.items[0].link");
        assertThat(link).startsWith("/certificates/");
        mockMvc.perform(get("/api/v1/certificates/" + link.substring("/certificates/".length())))
                .andExpect(status().isOk());
    }

    @Test
    void newLesson_notifiesEnrolledLearnersOnceUntilRead() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        TestUser notEnrolled = registerUser();
        String slug = createCourse(admin);
        Integer sectionId = createSection(admin, slug);
        enroll(learner, slug);

        addLesson(admin, slug, sectionId, "Lesson one");
        addLesson(admin, slug, sectionId, "Lesson two");
        assertThat(types(learner)).containsExactly("new_lesson", "enrolled");
        assertThat(types(notEnrolled)).isEmpty();

        mockMvc.perform(as(learner, post(NOTIFICATIONS + "/read-all"))).andExpect(status().isOk());
        addLesson(admin, slug, sectionId, "Lesson three");
        assertThat(types(learner)).containsExactly("new_lesson", "new_lesson", "enrolled");
    }

    @Test
    void studentWithoutAccount_canStillBeEnrolled() throws Exception {
        TestUser admin = registerAdmin();
        Integer courseId = courseRepository.findBySlug(createCourse(admin)).orElseThrow().getId();
        String learnerRows = "select count(*) from notifications where type <> 'NEW_ENROLLMENT'";
        Long before = jdbcTemplate.queryForObject(learnerRows, Long.class);

        Map<String, Object> body = new HashMap<>();
        body.put("studentId", STUDENT_WITHOUT_ACCOUNT);
        body.put("courseId", courseId);
        mockMvc.perform(asJson(admin, post("/api/v1/enrollments"), body))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(learnerRows, Long.class)).isEqualTo(before);
        Mockito.verify(streamRegistry, never()).send(any(UUID.class), any(), anyLong());
    }

    @Test
    void newEnrollment_notifiesAdmins_unlessTheAlertIsOff() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();

        enroll(learner, createCourse(admin));

        assertThat(types(admin)).containsExactly("new_enrollment");
        assertThat(types(learner)).doesNotContain("new_enrollment");

        // Reading the settings creates their row
        mockMvc.perform(as(admin, get("/api/v1/settings"))).andExpect(status().isOk());
        jdbcTemplate.update("update platform_settings set notifications = notifications || '{\"newEnrollment\": false}'::jsonb");
        try {
            enroll(learner, createCourse(admin));
            assertThat(types(admin)).containsExactly("new_enrollment");
        } finally {
            jdbcTemplate.update("update platform_settings set notifications = notifications || '{\"newEnrollment\": true}'::jsonb");
        }
    }
}
