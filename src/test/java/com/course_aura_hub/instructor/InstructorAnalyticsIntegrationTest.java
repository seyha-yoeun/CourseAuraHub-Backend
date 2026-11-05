package com.course_aura_hub.instructor;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstructorAnalyticsIntegrationTest extends LearnerIntegrationTestSupport {

    private static String analytics(String slug) {
        return "/api/v1/instructor/courses/" + slug + "/analytics";
    }

    @Test
    void funnelShowsCompletionDropOffAndWatchDepth() throws Exception {
        TestUser admin = registerAdmin();
        String slug = createCourse(admin);
        int section = createSection(admin, slug);
        int intro = createLesson(admin, slug, section, "Intro", "article");
        int video = createLesson(admin, slug, section, "Deep dive", "video");

        TestUser finisher = registerUser();
        TestUser quitter = registerUser();
        enroll(finisher, slug);
        enroll(quitter, slug);
        complete(finisher, intro);
        complete(finisher, video);
        complete(quitter, intro);
        mockMvc.perform(asJson(quitter, put("/api/v1/me/lessons/" + video + "/position"), Map.of("positionSeconds", 120)))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(as(admin, get(analytics(slug))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.learners.enrolled").value(2))
                .andExpect(jsonPath("$.data.learners.completed").value(1))
                .andExpect(jsonPath("$.data.learners.completionRate").value(50.0))
                .andExpect(jsonPath("$.data.lessons[0].title").value("Intro"))
                .andExpect(jsonPath("$.data.lessons[0].completionRate").value(100.0))
                .andExpect(jsonPath("$.data.lessons[0].dropOff").value(0.0))
                .andExpect(jsonPath("$.data.lessons[0].video").doesNotExist())
                .andExpect(jsonPath("$.data.lessons[1].completionRate").value(50.0))
                .andExpect(jsonPath("$.data.lessons[1].dropOff").value(50.0))
                .andExpect(jsonPath("$.data.lessons[1].video.learnersWatching").value(1))
                .andExpect(jsonPath("$.data.lessons[1].video.averageWatchSeconds").value(120))
                .andExpect(jsonPath("$.data.reviews.count").value(0))
                .andExpect(jsonPath("$.data.reviews.distribution.5").value(0))
                .andExpect(jsonPath("$.data.assignments.pendingReview").value(0))
                .andExpect(jsonPath("$.data.monthly.length()").value(12))
                // Aggregates only: no learner identities anywhere
                .andExpect(jsonPath("$..email").doesNotExist());
    }

    @Test
    void onlyTheCoursesManagersSeeAnalytics() throws Exception {
        String slug = createCourse(registerAdmin());

        TestUser instructor = registerUser();
        userRepository.findByEmail(instructor.email()).ifPresent(entity -> {
            entity.setRole(Role.ROLE_INSTRUCTOR);
            userRepository.save(entity);
        });

        mockMvc.perform(as(instructor, get(analytics(slug)))).andExpect(status().isForbidden());
        mockMvc.perform(as(registerUser(), get(analytics(slug)))).andExpect(status().isForbidden());
        mockMvc.perform(get(analytics(slug))).andExpect(status().isUnauthorized());
    }

    private void complete(TestUser learner, int lessonId) throws Exception {
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonId + "/completion")))
                .andExpect(status().isOk());
    }

    private String createCourse(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Analytics " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.slug");
    }

    private int createSection(TestUser admin, String slug) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Basics")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private int createLesson(TestUser admin, String slug, int section, String title, String type) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + section + "/lessons"),
                        Map.of("title", title, "type", type, "durationMinutes", 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }
}
