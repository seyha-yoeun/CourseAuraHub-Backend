package com.course_aura_hub.prerequisite;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrerequisiteIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void aHardPrerequisiteBlocksEnrolling_untilItIsCompleted() throws Exception {
        TestUser admin = registerAdmin();
        String advanced = createCourse(admin, 0);
        setPrerequisites(admin, advanced, List.of(Map.of("courseSlug", FREE_COURSE))).andExpect(status().isOk());

        TestUser learner = registerUser();
        mockMvc.perform(as(learner, get("/api/v1/courses/" + advanced + "/prerequisites")))
                .andExpect(jsonPath("$.data.canEnroll").value(false))
                .andExpect(jsonPath("$.data.prerequisites[0].courseSlug").value(FREE_COURSE))
                .andExpect(jsonPath("$.data.prerequisites[0].completed").value(false));
        mockMvc.perform(asJson(learner, post("/api/v1/me/enrollments"), Map.of("courseSlug", advanced)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Git & GitHub Fundamentals")));

        enroll(learner, FREE_COURSE);
        List<Integer> lessons = jdbcTemplate.queryForList("""
                select l.id from lessons l join course_sections s on s.id = l.section_id join courses c on c.id = s.course_id
                where c.slug = ? order by s.position, l.position
                """, Integer.class, FREE_COURSE);
        for (Integer lesson : lessons) {
            mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lesson + "/completion"))).andExpect(status().isOk());
        }
        mockMvc.perform(as(learner, get("/api/v1/courses/" + advanced + "/prerequisites")))
                .andExpect(jsonPath("$.data.canEnroll").value(true));
        enroll(learner, advanced);
    }

    @Test
    void paidCoursesAreBlockedAtTheCart_andSoftPrerequisitesOnlyRecommend() throws Exception {
        TestUser admin = registerAdmin();
        String paid = createCourse(admin, 30);
        setPrerequisites(admin, paid, List.of(Map.of("courseSlug", FREE_COURSE))).andExpect(status().isOk());
        TestUser learner = registerUser();
        mockMvc.perform(as(learner, put("/api/v1/me/cart/" + paid))).andExpect(status().isBadRequest());

        String soft = createCourse(admin, 0);
        setPrerequisites(admin, soft, List.of(Map.of("courseSlug", FREE_COURSE, "hard", false))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses/" + soft + "/prerequisites"))
                .andExpect(jsonPath("$.data.canEnroll").value(true))
                .andExpect(jsonPath("$.data.prerequisites[0].hard").value(false))
                .andExpect(jsonPath("$.data.prerequisites[0].completed").doesNotExist());
        enroll(learner, soft);
    }

    @Test
    void cyclesSelfReferencesAndStrangersAreRejected() throws Exception {
        TestUser admin = registerAdmin();
        String first = createCourse(admin, 0);
        String second = createCourse(admin, 0);
        setPrerequisites(admin, second, List.of(Map.of("courseSlug", first))).andExpect(status().isOk());
        setPrerequisites(admin, first, List.of(Map.of("courseSlug", second))).andExpect(status().isBadRequest());
        setPrerequisites(admin, first, List.of(Map.of("courseSlug", first))).andExpect(status().isBadRequest());

        TestUser instructor = registerUser();
        userRepository.findByEmail(instructor.email()).ifPresent(user -> {
            user.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(user);
        });
        setPrerequisites(instructor, first, List.of(Map.of("courseSlug", FREE_COURSE))).andExpect(status().isForbidden());
        setPrerequisites(registerUser(), first, List.of()).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions setPrerequisites(TestUser user, String slug, List<Map<String, Object>> items)
            throws Exception {
        return mockMvc.perform(asJson(user, put("/api/v1/instructor/courses/" + slug + "/prerequisites"), Map.of("prerequisites", items)));
    }

    private String createCourse(TestUser admin, int price) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Next step " + System.nanoTime(), "status", "published", "price", price, "originalPrice", price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }
}
