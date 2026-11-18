package com.course_aura_hub.dashboard;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FunnelAnalyticsIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void theFunnelCountsDistinctVisitors_thenLearners() throws Exception {
        TestUser admin = registerAdmin();
        String slug = courseWithLesson(admin);

        event(slug, "view", "visitor-aaaa1111").andExpect(status().isNoContent());
        // The same visitor again within the window counts once
        event(slug, "view", "visitor-aaaa1111").andExpect(status().isNoContent());
        event(slug, "view", "visitor-bbbb2222").andExpect(status().isNoContent());
        event(slug, "add_to_cart", "visitor-bbbb2222").andExpect(status().isNoContent());

        TestUser learner = registerUser();
        enroll(learner, slug);
        completeFirstLesson(learner, slug);

        mockMvc.perform(as(admin, get("/api/v1/stats/funnel").param("course", slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps[0].key").value("view"))
                .andExpect(jsonPath("$.data.steps[0].count").value(2))
                .andExpect(jsonPath("$.data.steps[1].count").value(1))
                .andExpect(jsonPath("$.data.steps[1].fromPrevious").value(50.0))
                .andExpect(jsonPath("$.data.steps[2].count").value(0))
                .andExpect(jsonPath("$.data.steps[3].key").value("enrolled"))
                .andExpect(jsonPath("$.data.steps[3].count").value(1))
                .andExpect(jsonPath("$.data.steps[5].key").value("completed"))
                .andExpect(jsonPath("$.data.steps[5].count").value(1));
        Long stored = jdbcTemplate.queryForObject("""
                select count(*) from catalog_events e join courses c on c.id = e.course_id
                where c.slug = ? and e.visitor_hash like 'visitor%'
                """, Long.class, slug);
        org.assertj.core.api.Assertions.assertThat(stored).isZero();
    }

    @Test
    void badEventsAreRejected_andReportsAreAdminOnly() throws Exception {
        String slug = courseWithLesson(registerAdmin());

        event(slug, "purchase", "visitor-cccc3333").andExpect(status().isBadRequest());
        event("no-such-course", "view", "visitor-cccc3333").andExpect(status().isNotFound());

        TestUser learner = registerUser();
        mockMvc.perform(as(learner, get("/api/v1/stats/funnel"))).andExpect(status().isForbidden());
        mockMvc.perform(as(learner, get("/api/v1/stats/retention"))).andExpect(status().isForbidden());
        mockMvc.perform(as(registerAdmin(), get("/api/v1/stats/funnel")
                        .param("from", "2026-05-02").param("to", "2026-05-01")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retentionGroupsLearnersByJoiningMonth() throws Exception {
        TestUser admin = registerAdmin();
        String slug = courseWithLesson(admin);
        TestUser learner = registerUser();
        enroll(learner, slug);
        completeFirstLesson(learner, slug);

        mockMvc.perform(as(admin, get("/api/v1/stats/retention").param("months", "3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cohorts.length()").value(3))
                .andExpect(jsonPath("$.data.cohorts[2].month").value(YearMonth.now(ZoneOffset.UTC).toString()))
                .andExpect(jsonPath("$.data.cohorts[2].learners").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.cohorts[2].months[0].active").value(greaterThanOrEqualTo(1)));
    }

    private org.springframework.test.web.servlet.ResultActions event(String slug, String event, String visitor)
            throws Exception {
        return mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("courseSlug", slug, "event", event, "visitorId", visitor))));
    }

    private void completeFirstLesson(TestUser user, String slug) throws Exception {
        Integer lesson = jdbcTemplate.queryForObject("""
                select l.id from lessons l join course_sections s on s.id = l.section_id join courses c on c.id = s.course_id
                where c.slug = ? order by s.position, l.position limit 1
                """, Integer.class, slug);
        mockMvc.perform(as(user, put("/api/v1/me/lessons/" + lesson + "/completion"))).andExpect(status().isOk());
    }

    private String courseWithLesson(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Funnel " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        String section = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Basics")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Only lesson", "type", "article", "durationMinutes", 5)))
                .andExpect(status().isCreated());
        return slug;
    }
}
