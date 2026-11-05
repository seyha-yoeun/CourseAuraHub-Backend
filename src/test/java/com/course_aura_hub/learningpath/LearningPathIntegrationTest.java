package com.course_aura_hub.learningpath;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearningPathIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String ADMIN = "/api/v1/admin/learning-paths";

    private record TestCourse(String slug, int lessonId) {
    }

    @Test
    void finishingEveryCourseIssuesAPermanentPathCertificate() throws Exception {
        TestUser admin = registerAdmin();
        TestCourse first = createCourse(admin, "published", 0);
        TestCourse second = createCourse(admin, "published", 0);
        TestCourse hidden = createCourse(admin, "draft", 0);
        String path = createPath(admin, List.of(first.slug(), hidden.slug(), second.slug()));

        mockMvc.perform(get("/api/v1/learning-paths/" + path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseCount").value(2))
                .andExpect(jsonPath("$.data.courses[0].slug").value(first.slug()))
                .andExpect(jsonPath("$.data.courses[1].slug").value(second.slug()))
                .andExpect(jsonPath("$.data.courses[0].revenue").doesNotExist());

        TestUser learner = registerUser();
        mockMvc.perform(as(learner, post("/api/v1/me/learning-paths/" + path + "/enroll")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled.length()").value(2));

        complete(learner, first);
        mockMvc.perform(as(learner, get("/api/v1/me/learning-paths/" + path + "/progress")))
                .andExpect(jsonPath("$.data.completedCourses").value(1))
                .andExpect(jsonPath("$.data.percent").value(50.0))
                .andExpect(jsonPath("$.data.nextCourseSlug").value(second.slug()))
                .andExpect(jsonPath("$.data.certificate").doesNotExist());

        complete(learner, second);
        String progress = mockMvc.perform(as(learner, get("/api/v1/me/learning-paths/" + path + "/progress")))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.certificate.certificateNumber").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(progress, "$.data.certificate.certificateNumber");

        mockMvc.perform(as(learner, get("/api/v1/me/learning-paths")))
                .andExpect(jsonPath("$.data[0].certificate.certificateNumber").value(number));
        mockMvc.perform(as(learner, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].title").value("Learning path completed"));
        mockMvc.perform(as(learner, get("/api/v1/me/learning-paths/certificates/" + number + "/pdf")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        mockMvc.perform(as(registerUser(), get("/api/v1/me/learning-paths/certificates/" + number + "/pdf")))
                .andExpect(status().isNotFound());

        // Deleting the path keeps the certificate verifiable
        mockMvc.perform(as(admin, delete(ADMIN + "/" + path))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/certificates/" + number.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseTitle").value(org.hamcrest.Matchers.startsWith("Path ")))
                .andExpect(jsonPath("$.data.learnerName").value("Jane Learner"));
    }

    @Test
    void paidCoursesGoToTheCartInsteadOfEnrolling() throws Exception {
        TestUser admin = registerAdmin();
        TestCourse free = createCourse(admin, "published", 0);
        TestCourse paid = createCourse(admin, "published", 25);
        String path = createPath(admin, List.of(free.slug(), paid.slug()));

        TestUser learner = registerUser();
        enroll(learner, free.slug());
        mockMvc.perform(as(learner, post("/api/v1/me/learning-paths/" + path + "/enroll")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyEnrolled[0]").value(free.slug()))
                .andExpect(jsonPath("$.data.addedToCart[0]").value(paid.slug()))
                .andExpect(jsonPath("$.data.enrolled.length()").value(0));

        mockMvc.perform(get("/api/v1/learning-paths/" + path)).andExpect(jsonPath("$.data.price").value(25.0));
    }

    @Test
    void draftsStayHidden_andPublishingNeedsAPublishedCourse() throws Exception {
        TestUser admin = registerAdmin();
        TestCourse draftCourse = createCourse(admin, "draft", 0);

        mockMvc.perform(asJson(admin, post(ADMIN), Map.of(
                        "title", "Nope " + System.nanoTime(), "status", "published", "courseSlugs", List.of(draftCourse.slug()))))
                .andExpect(status().isBadRequest());

        String body = mockMvc.perform(asJson(admin, post(ADMIN), Map.of("title", "Draft path " + System.nanoTime())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        mockMvc.perform(get("/api/v1/learning-paths/" + slug)).andExpect(status().isNotFound());

        String list = mockMvc.perform(get("/api/v1/learning-paths")).andReturn().getResponse().getContentAsString();
        List<String> slugs = JsonPath.read(list, "$.data[*].slug");
        assertThat(slugs).doesNotContain(slug);

        mockMvc.perform(asJson(admin, put(ADMIN + "/" + slug), Map.of("courseSlugs", List.of("missing-" + System.nanoTime()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsManagePaths() throws Exception {
        TestUser learner = registerUser();
        mockMvc.perform(as(learner, get(ADMIN))).andExpect(status().isForbidden());
        mockMvc.perform(asJson(learner, post(ADMIN), Map.of("title", "Mine"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/learning-paths")).andExpect(status().isUnauthorized());
    }

    private void complete(TestUser learner, TestCourse course) throws Exception {
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + course.lessonId() + "/completion")))
                .andExpect(status().isOk());
    }

    private String createPath(TestUser admin, List<String> courseSlugs) throws Exception {
        String body = mockMvc.perform(asJson(admin, post(ADMIN), Map.of(
                        "title", "Path " + System.nanoTime(), "status", "published", "courseSlugs", courseSlugs)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }

    private TestCourse createCourse(TestUser admin, String status, int price) throws Exception {
        Map<String, Object> fields = new HashMap<>(Map.of("title", "Step " + System.nanoTime(), "status", status, "price", price));
        fields.put("originalPrice", price);
        String course = mockMvc.perform(asJson(admin, post("/api/v1/courses"), fields))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(course, "$.data.slug");
        String section = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "S")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        String lesson = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Only lesson", "type", "article", "durationMinutes", 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new TestCourse(slug, JsonPath.read(lesson, "$.data.id"));
    }
}
