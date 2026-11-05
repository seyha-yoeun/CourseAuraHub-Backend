package com.course_aura_hub.export;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExportIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void adminsDownloadCsv_andEachExportIsAudited() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        TestUser admin = registerAdmin();

        MockHttpServletResponse response = mockMvc.perform(as(admin, get("/api/v1/admin/exports/learners.csv")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).startsWith("text/csv");
        assertThat(response.getHeader("Content-Disposition")).contains("attachment").contains("learners-");
        String csv = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿student_id,name,email").contains(learner.email());

        String enrollments = mockMvc.perform(as(admin, get("/api/v1/admin/exports/enrollments.csv").param("from", "2000-01-01")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(enrollments).contains(FREE_COURSE).contains(learner.email());
        mockMvc.perform(as(admin, get("/api/v1/admin/exports/orders.csv").param("status", "paid"))).andExpect(status().isOk());

        mockMvc.perform(as(admin, get("/api/v1/admin/audit-log")).param("actor", admin.email()))
                .andExpect(jsonPath("$.data.items[?(@.action == 'ExportController.learners')].httpMethod").value("GET"));
    }

    @Test
    void badFiltersAreNormalErrors() throws Exception {
        TestUser admin = registerAdmin();
        mockMvc.perform(as(admin, get("/api/v1/admin/exports/orders.csv").param("status", "bogus")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(as(admin, get("/api/v1/admin/exports/orders.csv").param("from", "2026-02-01").param("to", "2026-01-01")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(registerUser(), get("/api/v1/admin/exports/learners.csv"))).andExpect(status().isForbidden());
    }

    @Test
    void instructorExportsHaveNamesButNoEmails_andOnlyTheirOwnCourse() throws Exception {
        TestUser instructor = registerInstructor();
        TestUser admin = registerAdmin();
        String slug = publishedCourse(instructor, admin);
        TestUser learner = registerUser();
        enroll(learner, slug);

        String csv = mockMvc.perform(as(instructor, get("/api/v1/instructor/courses/" + slug + "/exports/learners.csv")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("learner_name,enrolled_at").contains("Jane Learner").doesNotContain(learner.email()).doesNotContain("@");
        mockMvc.perform(as(instructor, get("/api/v1/instructor/courses/" + slug + "/exports/grades.csv"))).andExpect(status().isOk());

        mockMvc.perform(as(registerInstructor(), get("/api/v1/instructor/courses/" + slug + "/exports/learners.csv")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private TestUser registerInstructor() throws Exception {
        TestUser user = registerUser();
        userRepository.findByEmail(user.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(entity);
        });
        return user;
    }

    private String publishedCourse(TestUser instructor, TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(instructor, post("/api/v1/instructor/courses"), Map.of("title", "Export " + System.nanoTime())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.course.slug");
        String section = mockMvc.perform(asJson(instructor, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "One")))
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        mockMvc.perform(asJson(instructor, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                Map.of("title", "Intro", "type", "article", "durationMinutes", 5))).andExpect(status().isCreated());
        mockMvc.perform(as(instructor, post("/api/v1/instructor/courses/" + slug + "/submit"))).andExpect(status().isOk());
        mockMvc.perform(as(admin, post("/api/v1/admin/course-reviews/" + slug + "/approve"))).andExpect(status().isOk());
        return slug;
    }
}
