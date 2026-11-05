package com.course_aura_hub.quiz;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizRulesIntegrationTest extends LearnerIntegrationTestSupport {

    private record Quiz(String slug, Integer lessonId, Map<Integer, Integer> right, Map<Integer, Integer> wrong) {
    }

    @Test
    void attemptLimitStopsRetakes_untilStaffGrantMore() throws Exception {
        TestUser admin = registerAdmin();
        Quiz quiz = createQuiz(admin, 2, Map.of("maxAttempts", 1));
        TestUser learner = registerUser();
        enroll(learner, quiz.slug());

        submit(learner, quiz, quiz.wrong()).andExpect(status().isOk()).andExpect(jsonPath("$.data.passed").value(false));
        mockMvc.perform(as(learner, get(quizPath(quiz)))).andExpect(jsonPath("$.data.attemptsRemaining").value(0));
        submit(learner, quiz, quiz.right()).andExpect(status().isBadRequest());

        mockMvc.perform(asJson(admin, post("/api/v1/admin/lessons/" + quiz.lessonId() + "/quiz/attempt-grants"),
                        Map.of("studentId", studentId(learner), "extraAttempts", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptsRemaining").value(1));
        submit(learner, quiz, quiz.right()).andExpect(status().isOk()).andExpect(jsonPath("$.data.passed").value(true));
    }

    @Test
    void cooldownDelaysTheRetryAfterAFailure() throws Exception {
        TestUser admin = registerAdmin();
        Quiz quiz = createQuiz(admin, 2, Map.of("retryCooldownMinutes", 60));
        TestUser learner = registerUser();
        enroll(learner, quiz.slug());

        submit(learner, quiz, quiz.wrong()).andExpect(status().isOk());
        mockMvc.perform(as(learner, get(quizPath(quiz)))).andExpect(jsonPath("$.data.nextAttemptAt").isNotEmpty());
        submit(learner, quiz, quiz.right()).andExpect(status().isBadRequest());

        jdbcTemplate.update("update quiz_attempts set submitted_at = now() - interval '2 hours' where lesson_id = ?", quiz.lessonId());
        submit(learner, quiz, quiz.right()).andExpect(status().isOk()).andExpect(jsonPath("$.data.passed").value(true));
    }

    @Test
    void pooledQuiz_drawsQuestionsPerAttemptAndKeepsThemUntilSubmitted() throws Exception {
        TestUser admin = registerAdmin();
        Quiz quiz = createQuiz(admin, 4, Map.of("questionsPerAttempt", 2, "shuffle", true));
        TestUser learner = registerUser();
        enroll(learner, quiz.slug());

        mockMvc.perform(as(learner, get(quizPath(quiz))))
                .andExpect(jsonPath("$.data.startRequired").value(true))
                .andExpect(jsonPath("$.data.questions.length()").value(0));
        submit(learner, quiz, quiz.right()).andExpect(status().isBadRequest());

        List<Integer> drawn = drawnQuestions(start(learner, quiz));
        assertThat(drawn).hasSize(2).isSubsetOf(quiz.right().keySet());
        assertThat(drawnQuestions(start(learner, quiz))).isEqualTo(drawn);
        String reloaded = mockMvc.perform(as(learner, get(quizPath(quiz)))).andReturn().getResponse().getContentAsString();
        assertThat(drawnQuestions(reloaded)).isEqualTo(drawn);

        Map<Integer, Integer> answers = new HashMap<>();
        drawn.forEach(id -> answers.put(id, quiz.right().get(id)));
        submit(learner, quiz, answers)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.passed").value(true));
    }

    @Test
    void timedQuiz_scoresZeroAfterTheDeadline_andAnAbandonedAttemptCounts() throws Exception {
        TestUser admin = registerAdmin();
        Quiz quiz = createQuiz(admin, 2, Map.of("timeLimitMinutes", 5));
        TestUser learner = registerUser();
        enroll(learner, quiz.slug());

        start(learner, quiz);
        expireSessions(quiz);
        submit(learner, quiz, quiz.right())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scorePercent").value(0))
                .andExpect(jsonPath("$.data.passed").value(false));

        start(learner, quiz);
        expireSessions(quiz);
        String restarted = start(learner, quiz);
        assertThat((Integer) JsonPath.read(restarted, "$.data.attempts")).isEqualTo(2);
        assertThat((String) JsonPath.read(restarted, "$.data.expiresAt")).isNotEmpty();

        // A timed quiz accepts a partial answer sheet
        Integer first = quiz.right().keySet().iterator().next();
        submit(learner, quiz, Map.of(first, quiz.right().get(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scorePercent").value(50));
    }

    @Test
    void invalidRulesAndGrantsAreRejected() throws Exception {
        TestUser admin = registerAdmin();
        Quiz quiz = createQuiz(admin, 2, Map.of());

        mockMvc.perform(asJson(admin, put("/api/v1/admin/lessons/" + quiz.lessonId() + "/quiz"),
                        quizBody(2, Map.of("questionsPerAttempt", 3))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(admin, put("/api/v1/admin/lessons/" + quiz.lessonId() + "/quiz"),
                        quizBody(2, Map.of("maxAttempts", 0))))
                .andExpect(status().isBadRequest());

        TestUser outsider = registerUser();
        enroll(outsider, FREE_COURSE);
        mockMvc.perform(asJson(admin, post("/api/v1/admin/lessons/" + quiz.lessonId() + "/quiz/attempt-grants"),
                        Map.of("studentId", studentId(outsider), "extraAttempts", 1)))
                .andExpect(status().isNotFound());
        mockMvc.perform(asJson(outsider, post("/api/v1/admin/lessons/" + quiz.lessonId() + "/quiz/attempt-grants"),
                        Map.of("studentId", studentId(outsider), "extraAttempts", 1)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(admin, get("/api/v1/admin/lessons/" + quiz.lessonId() + "/quiz")))
                .andExpect(jsonPath("$.data.rules.shuffle").value(false))
                .andExpect(jsonPath("$.data.rules.maxAttempts").doesNotExist());
    }

    private void expireSessions(Quiz quiz) {
        jdbcTemplate.update("""
                update quiz_sessions set started_at = now() - interval '10 minutes', expires_at = now() - interval '5 minutes'
                where lesson_id = ? and submitted_at is null
                """, quiz.lessonId());
    }

    private String start(TestUser learner, Quiz quiz) throws Exception {
        return mockMvc.perform(as(learner, post(quizPath(quiz) + "/start")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static List<Integer> drawnQuestions(String body) {
        return JsonPath.read(body, "$.data.questions[*].id");
    }

    private org.springframework.test.web.servlet.ResultActions submit(TestUser learner, Quiz quiz, Map<Integer, Integer> answers)
            throws Exception {
        return mockMvc.perform(asJson(learner, post(quizPath(quiz) + "/attempts"), Map.of("answers", answers)));
    }

    private static String quizPath(Quiz quiz) {
        return "/api/v1/me/lessons/" + quiz.lessonId() + "/quiz";
    }

    private Integer studentId(TestUser user) {
        return jdbcTemplate.queryForObject(
                "select s.id from students s join users u on u.id = s.user_id where u.email = ?", Integer.class, user.email());
    }

    private Quiz createQuiz(TestUser admin, int questionCount, Map<String, Object> rules) throws Exception {
        String course = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Rules " + UUID.randomUUID(), "price", 0, "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(course, "$.data.slug");
        String section = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "S")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer sectionId = JsonPath.read(section, "$.data.id");
        String lesson = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Quiz", "type", "quiz", "durationMinutes", 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer lessonId = JsonPath.read(lesson, "$.data.id");

        String saved = mockMvc.perform(asJson(admin, put("/api/v1/admin/lessons/" + lessonId + "/quiz"), quizBody(questionCount, rules)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<Integer, Integer> right = new HashMap<>();
        Map<Integer, Integer> wrong = new HashMap<>();
        List<Map<String, Object>> questions = JsonPath.read(saved, "$.data.questions");
        for (Map<String, Object> question : questions) {
            List<Map<String, Object>> options = (List<Map<String, Object>>) question.get("options");
            for (Map<String, Object> option : options) {
                ((Boolean) option.get("correct") ? right : wrong).put((Integer) question.get("id"), (Integer) option.get("id"));
            }
        }
        return new Quiz(slug, lessonId, right, wrong);
    }

    private static Map<String, Object> quizBody(int questionCount, Map<String, Object> rules) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            questions.add(Map.of("prompt", "Question " + i, "options", List.of(
                    Map.of("text", "Right", "correct", true),
                    Map.of("text", "Wrong", "correct", false))));
        }
        Map<String, Object> body = new HashMap<>(rules);
        body.put("passPercent", 100);
        body.put("questions", questions);
        return body;
    }
}
