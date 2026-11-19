package com.course_aura_hub.learner;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaderboardIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void learnersAreRankedByXp_withShortNamesOnly() throws Exception {
        String slug = courseWithLessons(3);
        TestUser finisher = registerUser();
        TestUser starter = registerUser();
        List<Integer> lessons = lessons(slug);
        enroll(finisher, slug);
        enroll(starter, slug);
        lessons.forEach(lesson -> complete(finisher, lesson));
        complete(starter, lessons.getFirst());

        mockMvc.perform(as(starter, get("/api/v1/leaderboard").param("course", slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("week"))
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                // Three lessons and the certificate for finishing: 3 * 10 + 100
                .andExpect(jsonPath("$.data.entries[0].xp").value(130))
                .andExpect(jsonPath("$.data.entries[0].displayName").value("Jane L."))
                .andExpect(jsonPath("$.data.entries[0].you").value(false))
                .andExpect(jsonPath("$.data.entries[1].xp").value(10))
                .andExpect(jsonPath("$.data.me.rank").value(2))
                .andExpect(jsonPath("$.data.me.you").value(true))
                .andExpect(jsonPath("$..email").doesNotExist());
    }

    @Test
    void aLearnerCanHideFromLeaderboards() throws Exception {
        String slug = courseWithLessons(2);
        TestUser shy = registerUser();
        enroll(shy, slug);
        complete(shy, lessons(slug).getFirst());

        Map<String, Object> hide = new HashMap<>();
        hide.put("weeklyLessonsTarget", null);
        hide.put("leaderboardVisible", false);
        mockMvc.perform(asJson(shy, put("/api/v1/me/goals"), hide))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaderboardVisible").value(false));

        mockMvc.perform(as(shy, get("/api/v1/leaderboard").param("course", slug).param("scope", "month")))
                .andExpect(jsonPath("$.data.entries.length()").value(0))
                .andExpect(jsonPath("$.data.me").doesNotExist())
                .andExpect(jsonPath("$.data.visible").value(false));
    }

    @Test
    void theWeeklyGoal_tracksLessonsSinceMonday() throws Exception {
        String slug = courseWithLessons(2);
        TestUser learner = registerUser();
        enroll(learner, slug);

        mockMvc.perform(as(learner, get("/api/v1/me/goals")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklyLessonsTarget").doesNotExist())
                .andExpect(jsonPath("$.data.leaderboardVisible").value(true));
        mockMvc.perform(asJson(learner, put("/api/v1/me/goals"), Map.of("weeklyLessonsTarget", 0)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(learner, put("/api/v1/me/goals"), Map.of("weeklyLessonsTarget", 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklyLessonsTarget").value(4));

        complete(learner, lessons(slug).getFirst());
        mockMvc.perform(as(learner, get("/api/v1/me/goals").param("timeZone", "Asia/Phnom_Penh")))
                .andExpect(jsonPath("$.data.lessonsThisWeek").value(1))
                .andExpect(jsonPath("$.data.progressPercent").value(25))
                .andExpect(jsonPath("$.data.achieved").value(false));
    }

    @Test
    void badScopesAndUnknownCourses_areRejected() throws Exception {
        TestUser learner = registerUser();
        mockMvc.perform(as(learner, get("/api/v1/leaderboard").param("scope", "year"))).andExpect(status().isBadRequest());
        mockMvc.perform(as(learner, get("/api/v1/leaderboard").param("course", "no-such-course")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/leaderboard")).andExpect(status().isUnauthorized());
    }

    private String courseWithLessons(int count) throws Exception {
        TestUser admin = registerAdmin();
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Leaderboard " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        String section = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Basics")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        for (int i = 1; i <= count; i++) {
            mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                            Map.of("title", "Lesson " + i, "type", "article", "durationMinutes", 5)))
                    .andExpect(status().isCreated());
        }
        return slug;
    }

    private List<Integer> lessons(String slug) {
        return jdbcTemplate.queryForList("""
                select l.id from lessons l join course_sections s on s.id = l.section_id join courses c on c.id = s.course_id
                where c.slug = ? order by s.position, l.position
                """, Integer.class, slug);
    }

    private void complete(TestUser learner, Integer lessonId) {
        try {
            mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonId + "/completion"))).andExpect(status().isOk());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
