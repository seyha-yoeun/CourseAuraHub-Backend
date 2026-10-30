package com.course_aura_hub.curriculum;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VideoResumeIntegrationTest extends LearnerIntegrationTestSupport {

    private List<Integer> lessonIds(TestUser user, String type) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.sections[*].lessons[?(@.type == '" + type + "')].id");
    }

    private ResultActions save(TestUser user, Integer lessonId, int seconds) throws Exception {
        return mockMvc.perform(asJson(user, put("/api/v1/me/lessons/" + lessonId + "/position"), Map.of("positionSeconds", seconds)));
    }

    @Test
    void savedPosition_comesBackInCurriculumAndContinueLearning() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        Integer lessonId = lessonIds(learner, "video").getFirst();

        save(learner, lessonId, 100).andExpect(status().isOk());
        // Later saves overwrite, as a player does while it runs
        save(learner, lessonId, 245).andExpect(status().isOk()).andExpect(jsonPath("$.data.positionSeconds").value(245));

        mockMvc.perform(as(learner, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].resumePositionSeconds").value(245))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].resumePositionSeconds").doesNotExist());

        mockMvc.perform(as(learner, get("/api/v1/me/courses")))
                .andExpect(jsonPath("$.data[0].nextLesson.lessonId").value(lessonId))
                .andExpect(jsonPath("$.data[0].nextLesson.resumePositionSeconds").value(245));
    }

    @Test
    void completingLesson_clearsPositionAndStaysClearedWhenReopened() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        Integer lessonId = lessonIds(learner, "video").getFirst();
        save(learner, lessonId, 120).andExpect(status().isOk());

        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonId + "/completion"))).andExpect(status().isOk());
        mockMvc.perform(as(learner, delete("/api/v1/me/lessons/" + lessonId + "/completion"))).andExpect(status().isOk());

        mockMvc.perform(as(learner, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andExpect(jsonPath("$.data.sections[0].lessons[0].resumePositionSeconds").doesNotExist());
    }

    @Test
    void savingPosition_doesNotCountTowardProgress() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        save(learner, lessonIds(learner, "video").getFirst(), 300).andExpect(status().isOk());

        mockMvc.perform(as(learner, get("/api/v1/me/dashboard")))
                .andExpect(jsonPath("$.data.lessonsCompleted").value(0))
                .andExpect(jsonPath("$.data.streak.currentDays").value(0));
    }

    @Test
    void position_requiresEnrollmentVideoAndValidRange() throws Exception {
        TestUser learner = registerUser();
        Integer video = lessonIds(learner, "video").getFirst();
        save(learner, video, 10).andExpect(status().isForbidden());

        enroll(learner, FREE_COURSE);
        save(learner, video, -1).andExpect(status().isBadRequest());
        save(learner, lessonIds(learner, "article").getFirst(), 10).andExpect(status().isBadRequest());
    }
}
