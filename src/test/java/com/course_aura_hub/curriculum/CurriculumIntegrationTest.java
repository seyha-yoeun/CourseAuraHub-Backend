package com.course_aura_hub.curriculum;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurriculumIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void curriculum_locksContentUntilEnrolled() throws Exception {
        TestUser learner = registerUser();

        mockMvc.perform(as(learner, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(false))
                .andExpect(jsonPath("$.data.progress").doesNotExist())
                // First lesson is a free preview, the second is locked
                .andExpect(jsonPath("$.data.sections[0].lessons[0].preview").value(true))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].contentUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.sections[0].lessons[1].locked").value(true))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].contentUrl").doesNotExist());

        enroll(learner, FREE_COURSE);

        mockMvc.perform(as(learner, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(true))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].locked").value(false))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].contentUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.progress.progressPercent").value(0));
    }

    @Test
    void completingEveryLesson_marksCourseCompleted() throws Exception {
        TestUser learner = registerUser();
        List<Integer> lessonIds = lessonIds(learner, FREE_COURSE);

        // Not enrolled yet
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonIds.getFirst() + "/completion")))
                .andExpect(status().isForbidden());

        enroll(learner, FREE_COURSE);

        for (Integer lessonId : lessonIds) {
            mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonId + "/completion")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.lessonCompleted").value(true));
        }

        mockMvc.perform(as(learner, get("/api/v1/me/courses")))
                .andExpect(jsonPath("$.data[0].progress.completed").value(true))
                .andExpect(jsonPath("$.data[0].progress.progressPercent").value(100))
                .andExpect(jsonPath("$.data[0].lastActivityAt").exists());

        // Un-completing a lesson re-opens the course
        mockMvc.perform(as(learner, delete("/api/v1/me/lessons/" + lessonIds.getLast() + "/completion")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseProgress.completed").value(false))
                .andExpect(jsonPath("$.data.courseProgress.completedLessons").value(lessonIds.size() - 1));
    }

    @Test
    void adminCurriculumChanges_updateCourseTotals() throws Exception {
        TestUser admin = registerAdmin();
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"), Map.of(
                        "title", "Curriculum Test " + UUID.randomUUID(),
                        "price", 0,
                        "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");

        String sectionBody = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"),
                        Map.of("title", "Intro")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer sectionId = JsonPath.read(sectionBody, "$.data.id");

        String lessonBody = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Lesson one", "type", "video", "durationMinutes", 90)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer firstLessonId = JsonPath.read(lessonBody, "$.data.id");

        mockMvc.perform(as(admin, get("/api/v1/courses/" + slug)))
                .andExpect(jsonPath("$.data.totalLessons").value(1))
                .andExpect(jsonPath("$.data.totalHours").value(1.5));

        // A learner who finished the only lesson is completed...
        TestUser learner = registerUser();
        enroll(learner, slug);
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + firstLessonId + "/completion")))
                .andExpect(jsonPath("$.data.courseProgress.completed").value(true));

        // ...until a new lesson is added
        mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Lesson two", "durationMinutes", 30)))
                .andExpect(status().isCreated());

        mockMvc.perform(as(learner, get("/api/v1/courses/" + slug + "/curriculum")))
                .andExpect(jsonPath("$.data.totalLessons").value(2))
                .andExpect(jsonPath("$.data.progress.completed").value(false))
                .andExpect(jsonPath("$.data.progress.progressPercent").value(50));

        // Learners cannot edit curriculum
        mockMvc.perform(asJson(learner, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Nope")))
                .andExpect(status().isForbidden());
    }

    private List<Integer> lessonIds(TestUser user, String slug) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/courses/" + slug + "/curriculum")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.sections[*].lessons[*].id");
    }
}
