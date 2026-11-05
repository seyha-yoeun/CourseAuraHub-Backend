package com.course_aura_hub.revision;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseRevisionIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String COURSES = "/api/v1/instructor/courses";

    @Test
    void liveChangesWaitForApproval_thenGoLiveAndReachLearners() throws Exception {
        TestUser instructor = registerInstructor();
        TestUser admin = registerAdmin();
        String slug = publishedCourse(instructor, admin);
        TestUser learner = registerUser();
        enroll(learner, slug);

        String body = mockMvc.perform(asJson(instructor, put(COURSES + "/" + slug), Map.of("title", "Renamed course", "price", 19.5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course.title").value(org.hamcrest.Matchers.startsWith("Live ")))
                .andExpect(jsonPath("$.data.pendingRevision.changes.length()").value(2))
                .andExpect(jsonPath("$.data.pendingRevision.changes[0].field").value("title"))
                .andExpect(jsonPath("$.data.pendingRevision.changes[0].after").value("Renamed course"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.pendingRevision.id");
        mockMvc.perform(get("/api/v1/courses/" + slug)).andExpect(jsonPath("$.data.title").value(org.hamcrest.Matchers.startsWith("Live ")));

        mockMvc.perform(asJson(instructor, patch(COURSES + "/" + slug + "/revisions/pending"), Map.of("summary", "Clearer title")))
                .andExpect(jsonPath("$.data.summary").value("Clearer title"));
        String queue = mockMvc.perform(as(admin, get("/api/v1/admin/course-revisions")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(queue, "$.data[*].id");
        assertThat(ids).contains(id);

        mockMvc.perform(as(admin, post("/api/v1/admin/course-revisions/" + id + "/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"));
        mockMvc.perform(get("/api/v1/courses/" + slug))
                .andExpect(jsonPath("$.data.title").value("Renamed course"))
                .andExpect(jsonPath("$.data.price").value(19.5));
        mockMvc.perform(as(learner, get("/api/v1/me/notifications")))
                .andExpect(jsonPath("$.data.items[0].type").value("course_updated"))
                .andExpect(jsonPath("$.data.items[0].message").value("Clearer title"));
        mockMvc.perform(get("/api/v1/courses/" + slug + "/changelog"))
                .andExpect(jsonPath("$.data[0].fields.length()").value(1))
                .andExpect(jsonPath("$.data[0].fields[0]").value("title"));
    }

    @Test
    void lowRiskFieldsApplyAtOnce_andEditingBackDropsTheRevision() throws Exception {
        TestUser instructor = registerInstructor();
        String slug = publishedCourse(instructor, registerAdmin());

        mockMvc.perform(asJson(instructor, put(COURSES + "/" + slug), Map.of("tags", List.of("fresh"))))
                .andExpect(jsonPath("$.data.course.tags[0]").value("fresh"))
                .andExpect(jsonPath("$.data.pendingRevision").doesNotExist());

        String live = JsonPath.read(mockMvc.perform(get("/api/v1/courses/" + slug)).andReturn().getResponse().getContentAsString(), "$.data.description");
        mockMvc.perform(asJson(instructor, put(COURSES + "/" + slug), Map.of("description", "Brand new pitch")))
                .andExpect(jsonPath("$.data.pendingRevision.changes[0].field").value("description"));
        mockMvc.perform(asJson(instructor, put(COURSES + "/" + slug), Map.of("description", live)))
                .andExpect(jsonPath("$.data.pendingRevision").doesNotExist());
    }

    @Test
    void sendingChangesBackKeepsTheLiveCourse_andOnlyItsManagersSeeRevisions() throws Exception {
        TestUser instructor = registerInstructor();
        TestUser admin = registerAdmin();
        String slug = publishedCourse(instructor, admin);
        String body = mockMvc.perform(asJson(instructor, put(COURSES + "/" + slug), Map.of("level", "advanced")))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(body, "$.data.pendingRevision.id");

        mockMvc.perform(asJson(admin, post("/api/v1/admin/course-revisions/" + id + "/reject"), Map.of("note", "")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(admin, post("/api/v1/admin/course-revisions/" + id + "/reject"), Map.of("note", "Keep it beginner-friendly")))
                .andExpect(jsonPath("$.data.status").value("rejected"));
        mockMvc.perform(get("/api/v1/courses/" + slug)).andExpect(jsonPath("$.data.level").value("Beginner"));
        mockMvc.perform(as(instructor, get(COURSES + "/" + slug + "/revisions")))
                .andExpect(jsonPath("$.data[0].reviewNote").value("Keep it beginner-friendly"));

        mockMvc.perform(as(registerInstructor(), get(COURSES + "/" + slug + "/revisions"))).andExpect(status().isForbidden());
        mockMvc.perform(as(instructor, get("/api/v1/admin/course-revisions"))).andExpect(status().isForbidden());

        mockMvc.perform(asJson(instructor, put(COURSES + "/" + slug), Map.of("title", "Another try"))).andExpect(status().isOk());
        mockMvc.perform(as(instructor, delete(COURSES + "/" + slug + "/revisions/pending"))).andExpect(status().isOk());
        mockMvc.perform(as(instructor, get(COURSES + "/" + slug)))
                .andExpect(jsonPath("$.data.pendingRevision").doesNotExist());
    }

    @Test
    void draftsAndAdminEditsChangeImmediately() throws Exception {
        TestUser instructor = registerInstructor();
        String draft = createCourse(instructor);
        mockMvc.perform(asJson(instructor, put(COURSES + "/" + draft), Map.of("title", "Draft rename")))
                .andExpect(jsonPath("$.data.course.title").value("Draft rename"))
                .andExpect(jsonPath("$.data.pendingRevision").doesNotExist());

        TestUser admin = registerAdmin();
        String live = publishedCourse(instructor, admin);
        mockMvc.perform(asJson(admin, put(COURSES + "/" + live), Map.of("title", "Admin rename")))
                .andExpect(jsonPath("$.data.course.title").value("Admin rename"));
    }

    private TestUser registerInstructor() throws Exception {
        TestUser user = registerUser();
        userRepository.findByEmail(user.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(entity);
        });
        return user;
    }

    private String createCourse(TestUser instructor) throws Exception {
        String body = mockMvc.perform(asJson(instructor, post(COURSES), Map.of("title", "Live " + System.nanoTime())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.course.slug");
    }

    private String publishedCourse(TestUser instructor, TestUser admin) throws Exception {
        String slug = createCourse(instructor);
        String section = mockMvc.perform(asJson(instructor, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "One")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        mockMvc.perform(asJson(instructor, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Intro", "type", "article", "durationMinutes", 5)))
                .andExpect(status().isCreated());
        mockMvc.perform(as(instructor, post(COURSES + "/" + slug + "/submit"))).andExpect(status().isOk());
        mockMvc.perform(as(admin, post("/api/v1/admin/course-reviews/" + slug + "/approve"))).andExpect(status().isOk());
        return slug;
    }
}
