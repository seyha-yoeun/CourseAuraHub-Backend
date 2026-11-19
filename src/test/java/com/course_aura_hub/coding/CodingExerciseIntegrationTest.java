package com.course_aura_hub.coding;

import com.course_aura_hub.coding.service.CodeRunner;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.coding.judge0-url=http://judge0.test")
class CodingExerciseIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String HIDDEN_INPUT = "1000 2345";

    @MockitoBean
    private CodeRunner codeRunner;

    /** A pretend sandbox: "sum" programs add the two numbers they read; others misbehave in named ways. */
    @BeforeEach
    void pretendSandbox() {
        reset(codeRunner);
        when(codeRunner.run(anyInt(), anyString(), anyString(), any(), anyInt())).thenAnswer(call -> {
            String code = call.getArgument(1);
            String stdin = call.getArgument(2);
            int sum = Arrays.stream(stdin.trim().split("\\s+")).mapToInt(Integer::parseInt).sum();
            return switch (code) {
                case "sum" -> new CodeRunner.RunOutcome("accepted", sum + "  \n\n", "", "");
                case "examples-only" -> new CodeRunner.RunOutcome("accepted", stdin.startsWith("2 ") ? "5\n" : "0\n", "", "");
                case "leak" -> stdin.startsWith("2 ")
                        ? new CodeRunner.RunOutcome("accepted", "5\n", "", "")
                        : new CodeRunner.RunOutcome("runtime_error", "", "Traceback: input was " + stdin, "");
                case "broken" -> new CodeRunner.RunOutcome("compile_error", "", "", "SyntaxError: invalid syntax");
                default -> new CodeRunner.RunOutcome("accepted", "0\n", "", "");
            };
        });
    }

    @Test
    void passingEveryTestCompletesTheLesson_andHiddenTestsStayHidden() throws Exception {
        TestUser admin = registerAdmin();
        Lesson lesson = codeLesson(admin);
        TestUser learner = registerUser();
        enroll(learner, lesson.slug());

        String body = mockMvc.perform(as(learner, get(learnerPath(lesson.id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examples[0].input").value("2 3"))
                .andExpect(jsonPath("$.data.hiddenTests").value(2))
                .andExpect(jsonPath("$.data.runsEnabled").value(true))
                .andExpect(jsonPath("$.data.tests").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(HIDDEN_INPUT);

        // Can't be ticked off without passing
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lesson.id() + "/completion"))).andExpect(status().isBadRequest());

        run(learner, lesson.id(), "wrong")
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.verdict").value("wrong_answer"))
                .andExpect(jsonPath("$.data.examples[0].actualOutput").value("0\n"));
        run(learner, lesson.id(), "broken")
                .andExpect(jsonPath("$.data.verdict").value("compile_error"))
                .andExpect(jsonPath("$.data.message").value("SyntaxError: invalid syntax"));

        String hidden = run(learner, lesson.id(), "examples-only")
                .andExpect(jsonPath("$.data.verdict").value("wrong_answer"))
                .andExpect(jsonPath("$.data.passedTests").value(1))
                .andExpect(jsonPath("$.data.message").value("Hidden test 1 gave a different answer"))
                .andReturn().getResponse().getContentAsString();
        String leaked = run(learner, lesson.id(), "leak")
                .andExpect(jsonPath("$.data.verdict").value("runtime_error"))
                .andExpect(jsonPath("$.data.message").value("Hidden test 1 crashed"))
                .andReturn().getResponse().getContentAsString();
        assertThat(hidden + leaked).doesNotContain(HIDDEN_INPUT).doesNotContain("3345");

        run(learner, lesson.id(), "sum")
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.passedTests").value(3))
                .andExpect(jsonPath("$.data.totalTests").value(3))
                .andExpect(jsonPath("$.data.lessonCompleted").value(true));
        mockMvc.perform(as(learner, get(learnerPath(lesson.id()))))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.lastSubmission.code").value("sum"));
        Integer progress = jdbcTemplate.queryForObject(
                "select count(*) from lesson_progress where lesson_id = ?", Integer.class, lesson.id());
        assertThat(progress).isEqualTo(1);
    }

    @Test
    void onlyStaffWriteExercises_andOnlyEnrolledLearnersRunThem() throws Exception {
        TestUser admin = registerAdmin();
        Lesson lesson = codeLesson(admin);
        TestUser learner = registerUser();

        mockMvc.perform(as(admin, get("/api/v1/instructor/lessons/" + lesson.id() + "/exercise")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tests[0].input").value(HIDDEN_INPUT));
        mockMvc.perform(as(learner, get("/api/v1/instructor/lessons/" + lesson.id() + "/exercise")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(admin, put("/api/v1/instructor/lessons/" + lesson.id() + "/exercise"),
                        exercise("cobol")))
                .andExpect(status().isBadRequest());

        run(learner, lesson.id(), "sum").andExpect(status().isForbidden());
        // Staff can try their own exercise; nothing is saved for them
        run(admin, lesson.id(), "sum").andExpect(jsonPath("$.data.passed").value(true));
        Integer saved = jdbcTemplate.queryForObject("select count(*) from code_submissions where lesson_id = ?",
                Integer.class, lesson.id());
        assertThat(saved).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions run(TestUser user, int lessonId, String code) throws Exception {
        return mockMvc.perform(asJson(user, post(learnerPath(lessonId) + "/runs"), Map.of("code", code)));
    }

    private static String learnerPath(int lessonId) {
        return "/api/v1/me/lessons/" + lessonId + "/exercise";
    }

    private static Map<String, Object> exercise(String language) {
        return Map.of(
                "language", language,
                "instructions", "Print the sum of the two numbers.",
                "starterCode", "a, b = map(int, input().split())",
                "examples", List.of(Map.of("input", "2 3", "expectedOutput", "5")),
                "tests", List.of(Map.of("input", HIDDEN_INPUT, "expectedOutput", "3345"),
                        Map.of("input", "1 1", "expectedOutput", "2")));
    }

    private record Lesson(String slug, int id) {
    }

    private Lesson codeLesson(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Coding " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        String section = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Basics")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        String lesson = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Add two numbers", "type", "code", "durationMinutes", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("code"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(lesson, "$.data.id");
        mockMvc.perform(asJson(admin, put("/api/v1/instructor/lessons/" + id + "/exercise"), exercise("python")))
                .andExpect(status().isOk());
        return new Lesson(slug, id);
    }
}
