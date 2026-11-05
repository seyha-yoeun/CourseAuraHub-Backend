package com.course_aura_hub.audit;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuditIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String LOG = "/api/v1/admin/audit-log";

    @Test
    void adminWritesAreRecordedWithTheirOutcome() throws Exception {
        TestUser admin = registerAdmin();
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"), Map.of("title", "Audited " + System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        mockMvc.perform(as(admin, delete("/api/v1/courses/missing-" + System.nanoTime())))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(admin, delete("/api/v1/courses/" + slug)))
                .andExpect(status().isOk());

        mockMvc.perform(as(admin, get(LOG)).param("actor", admin.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items[0].action").value("CourseController.delete"))
                .andExpect(jsonPath("$.data.items[0].resource").value("courses"))
                .andExpect(jsonPath("$.data.items[0].resourceId").value(slug))
                .andExpect(jsonPath("$.data.items[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].actorRole").value("ADMIN"))
                .andExpect(jsonPath("$.data.items[0].requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.items[1].status").value(404))
                .andExpect(jsonPath("$.data.items[1].outcome").value("FAILURE"))
                .andExpect(jsonPath("$.data.items[2].action").value("CourseController.create"))
                .andExpect(jsonPath("$.data.items[2].status").value(201));

        mockMvc.perform(as(admin, get(LOG)).param("actor", admin.email()).param("outcome", "failure"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(as(admin, get(LOG)).param("resource", "courses").param("resourceId", slug))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void instructorWritesAreRecordedButLearnerSelfServiceIsNot() throws Exception {
        TestUser instructor = registerUser();
        userRepository.findByEmail(instructor.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(entity);
        });
        mockMvc.perform(asJson(instructor, post("/api/v1/instructor/courses"), Map.of("title", "Mine " + System.nanoTime())))
                .andExpect(status().isCreated());

        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        mockMvc.perform(asJson(learner, post("/api/v1/courses"), Map.of("title", "Nope"))).andExpect(status().isForbidden());

        TestUser admin = registerAdmin();
        mockMvc.perform(as(admin, get(LOG)).param("actor", instructor.email()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].actorRole").value("INSTRUCTOR"))
                .andExpect(jsonPath("$.data.items[0].action").value("InstructorController.create"));
        mockMvc.perform(as(admin, get(LOG)).param("actor", learner.email()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void onlyAdminsReadTheLog_andBadFiltersAreRejected() throws Exception {
        mockMvc.perform(as(registerUser(), get(LOG))).andExpect(status().isForbidden());
        TestUser admin = registerAdmin();
        mockMvc.perform(as(admin, get(LOG)).param("outcome", "maybe")).andExpect(status().isBadRequest());
        mockMvc.perform(as(admin, get(LOG))
                        .param("from", "2026-02-01T00:00:00Z").param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
