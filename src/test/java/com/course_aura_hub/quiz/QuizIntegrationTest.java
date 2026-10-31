package com.course_aura_hub.quiz;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizIntegrationTest extends LearnerIntegrationTestSupport {

    private record Course(String slug, Integer lessonId) {
    }

    private Course courseWithQuiz(TestUser admin) throws Exception {
        String course = mockMvc.perform(asJson(admin, post("/api/v1/courses"), Map.of(
                        "title", "Quiz Test " + UUID.randomUUID(),
                        "price", 0,
                        "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(course, "$.data.slug");

        String section = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Section")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer sectionId = JsonPath.read(section, "$.data.id");

        String lesson = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Final quiz", "type", "quiz", "durationMinutes", 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Course(slug, JsonPath.read(lesson, "$.data.id"));
    }

    private String saveQuiz(TestUser admin, Integer lessonId) throws Exception {
        return mockMvc.perform(asJson(admin, put("/api/v1/admin/lessons/" + lessonId + "/quiz"), Map.of(
                        "passPercent", 100,
                        "questions", List.of(Map.of(
                                "prompt", "What is 2 + 2?",
                                "explanation", "Basic addition.",
                                "options", List.of(
                                        Map.of("text", "3", "correct", false),
                                        Map.of("text", "4", "correct", true))))
                )))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> answer(Integer questionId, Integer optionId) {
        Map<String, Object> answers = new HashMap<>();
        answers.put(String.valueOf(questionId), optionId);
        return Map.of("answers", answers);
    }

    @Test
    void learners_neverSeeTheAnswersBeforePassing() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        Course course = courseWithQuiz(admin);
        saveQuiz(admin, course.lessonId());
        enroll(learner, course.slug());

        mockMvc.perform(as(learner, get("/api/v1/me/lessons/" + course.lessonId() + "/quiz")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(1))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("correct"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Basic addition"))));
    }

    @Test
    void quiz_mustBePassedToCompleteTheLesson() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        Course course = courseWithQuiz(admin);
        String saved = saveQuiz(admin, course.lessonId());
        Integer questionId = JsonPath.read(saved, "$.data.questions[0].id");
        Integer wrong = JsonPath.read(saved, "$.data.questions[0].options[0].id");
        Integer right = JsonPath.read(saved, "$.data.questions[0].options[1].id");
        enroll(learner, course.slug());
        String attempts = "/api/v1/me/lessons/" + course.lessonId() + "/quiz/attempts";

        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + course.lessonId() + "/completion")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asJson(learner, post(attempts), answer(questionId, wrong)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.scorePercent").value(0))
                .andExpect(jsonPath("$.data.questions[0].correctOptionId").doesNotExist());

        mockMvc.perform(asJson(learner, post(attempts), answer(questionId, right)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.questions[0].correctOptionId").value(right))
                .andExpect(jsonPath("$.data.questions[0].explanation").value("Basic addition."))
                .andExpect(jsonPath("$.data.progress.courseProgress.completed").value(true));

        // The course's only lesson is done, so the certificate is earned
        String certificates = mockMvc.perform(as(learner, get("/api/v1/me/certificates")))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(certificates, "$.data")).hasSize(1);
    }

    @Test
    void submitting_requiresEveryAnswer() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        Course course = courseWithQuiz(admin);
        saveQuiz(admin, course.lessonId());
        enroll(learner, course.slug());

        mockMvc.perform(asJson(learner, post("/api/v1/me/lessons/" + course.lessonId() + "/quiz/attempts"), answer(999999, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quizWithoutQuestions_completesLikeAnyLesson() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        Course course = courseWithQuiz(admin);
        enroll(learner, course.slug());

        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + course.lessonId() + "/completion")))
                .andExpect(status().isOk());
    }

    @Test
    void outsiders_cannotReadOrTakeAQuiz() throws Exception {
        TestUser admin = registerAdmin();
        TestUser learner = registerUser();
        Course course = courseWithQuiz(admin);
        saveQuiz(admin, course.lessonId());

        mockMvc.perform(as(learner, get("/api/v1/me/lessons/" + course.lessonId() + "/quiz")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(learner, get("/api/v1/admin/lessons/" + course.lessonId() + "/quiz")))
                .andExpect(status().isForbidden());
    }

    @Test
    void savingAQuiz_needsExactlyOneCorrectOption() throws Exception {
        TestUser admin = registerAdmin();
        Course course = courseWithQuiz(admin);

        mockMvc.perform(asJson(admin, put("/api/v1/admin/lessons/" + course.lessonId() + "/quiz"), Map.of(
                        "passPercent", 70,
                        "questions", List.of(Map.of(
                                "prompt", "Pick",
                                "options", List.of(
                                        Map.of("text", "A", "correct", true),
                                        Map.of("text", "B", "correct", true))))
                )))
                .andExpect(status().isBadRequest());
    }
}
