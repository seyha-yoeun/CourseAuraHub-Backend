package com.course_aura_hub.assistant;

import com.course_aura_hub.assistant.service.ClaudeGateway;
import com.course_aura_hub.assistant.service.QuizDraftOutput;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String STUDY_TEXT = "Git stores snapshots. A commit records a snapshot with a message.";

    @MockitoBean
    private ClaudeGateway claude;

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void pretendClaude() {
        reset(claude);
        when(claude.available()).thenReturn(true);
        when(claude.streamAnswer(anyString(), any(), any())).thenAnswer(call -> {
            Consumer<String> onText = call.getArgument(2);
            onText.accept("A commit is ");
            onText.accept("a snapshot.");
            return new ClaudeGateway.Answer("A commit is a snapshot.", 100, 20, false);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEnrolledLearnerGetsAStreamedAnswer_fromTheLessonText_withoutTheirDetails() throws Exception {
        TestUser admin = registerAdmin();
        Lesson lesson = lessonWithText(admin);
        TestUser learner = registerUser();
        enroll(learner, lesson.slug());

        HttpResponse<String> first = ask(learner, lesson.id(), Map.of("message", "What is a commit?"));
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/event-stream");
        assertThat(first.body()).contains("event:conversation", "event:delta", "a snapshot.", "event:done");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<ClaudeGateway.Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(claude).streamAnswer(system.capture(), turns.capture(), any());
        assertThat(system.getValue()).contains(STUDY_TEXT).doesNotContain(learner.email()).doesNotContain("Jane");
        assertThat(turns.getValue()).containsExactly(new ClaudeGateway.Turn(true, "What is a commit?"));

        String conversations = mockMvc.perform(as(learner, get(base(lesson.id()) + "/conversations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].messages.length()").value(2))
                .andExpect(jsonPath("$.data[0].messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.data[0].messages[1].content").value("A commit is a snapshot."))
                .andReturn().getResponse().getContentAsString();
        Number conversationId = JsonPath.read(conversations, "$.data[0].id");
        mockMvc.perform(as(learner, get("/api/v1/me/assistant/usage")))
                .andExpect(jsonPath("$.data.tokensUsed").value(120));

        // A follow-up carries the earlier turns
        ask(learner, lesson.id(), Map.of("conversationId", conversationId.longValue(), "message", "And a branch?"));
        verify(claude, times(2)).streamAnswer(anyString(), turns.capture(), any());
        assertThat(turns.getValue()).hasSize(3);
        assertThat(turns.getValue().get(1)).isEqualTo(new ClaudeGateway.Turn(false, "A commit is a snapshot."));
    }

    @Test
    void outsidersAndLearnersOverTheirLimitAreTurnedAway() throws Exception {
        TestUser admin = registerAdmin();
        Lesson lesson = lessonWithText(admin);

        TestUser outsider = registerUser();
        mockMvc.perform(asJson(outsider, post(base(lesson.id()) + "/messages"), Map.of("message", "Hi")))
                .andExpect(status().isForbidden());

        TestUser learner = registerUser();
        enroll(learner, lesson.slug());
        jdbcTemplate.update("""
                insert into assistant_usage (student_id, usage_date, requests, tokens)
                select s.id, (now() at time zone 'utc')::date, 1, 100000 from students s
                join users u on u.id = s.user_id where u.email = ?
                """, learner.email());
        mockMvc.perform(asJson(learner, post(base(lesson.id()) + "/messages"), Map.of("message", "Hi")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asJson(learner, put("/api/v1/instructor/lessons/" + lesson.id() + "/assistant-source"),
                        Map.of("body", "mine now")))
                .andExpect(status().isForbidden());
    }

    @Test
    void summariesAreMadeOnce_andQuizDraftsKeepOnlyValidQuestions() throws Exception {
        TestUser admin = registerAdmin();
        Lesson lesson = lessonWithText(admin);
        TestUser learner = registerUser();
        enroll(learner, lesson.slug());
        when(claude.complete(anyString(), anyString(), anyLong()))
                .thenReturn(new ClaudeGateway.Answer("- Commits are snapshots", 50, 10, false));

        mockMvc.perform(as(learner, get("/api/v1/me/lessons/" + lesson.id() + "/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("- Commits are snapshots"));
        mockMvc.perform(as(learner, get("/api/v1/me/lessons/" + lesson.id() + "/summary")))
                .andExpect(jsonPath("$.data.summary").value("- Commits are snapshots"));
        verify(claude, times(1)).complete(anyString(), anyString(), anyLong());

        when(claude.draftQuiz(anyString(), anyString())).thenReturn(draft());
        mockMvc.perform(asJson(admin, post("/api/v1/instructor/lessons/" + lesson.id() + "/quiz/draft"), Map.of("count", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].prompt").value("What does a commit record?"))
                .andExpect(jsonPath("$.data[0].options[0].correct").value(true));
        mockMvc.perform(asJson(learner, post("/api/v1/instructor/lessons/" + lesson.id() + "/quiz/draft"), Map.of("count", 2)))
                .andExpect(status().isForbidden());
    }

    private static QuizDraftOutput draft() {
        QuizDraftOutput draft = new QuizDraftOutput();
        draft.questions = List.of(question("What does a commit record?", true, false),
                question("Which two are right?", true, true));
        return draft;
    }

    private static QuizDraftOutput.Question question(String prompt, boolean first, boolean second) {
        QuizDraftOutput.Question question = new QuizDraftOutput.Question();
        question.prompt = prompt;
        question.explanation = "From the lesson.";
        question.options = List.of(option("A snapshot", first), option("A branch", second));
        return question;
    }

    private static QuizDraftOutput.Option option(String text, boolean correct) {
        QuizDraftOutput.Option option = new QuizDraftOutput.Option();
        option.text = text;
        option.correct = correct;
        return option;
    }

    private HttpResponse<String> ask(TestUser user, int lessonId, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + base(lessonId) + "/messages"))
                .header("Authorization", "Bearer " + user.accessToken())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String base(int lessonId) {
        return "/api/v1/me/lessons/" + lessonId + "/assistant";
    }

    private record Lesson(String slug, int id) {
    }

    private Lesson lessonWithText(TestUser admin) throws Exception {
        String body = mockMvc.perform(asJson(admin, post("/api/v1/courses"),
                        Map.of("title", "Assistant " + System.nanoTime(), "status", "published")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.data.slug");
        String section = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections"), Map.of("title", "Basics")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int sectionId = JsonPath.read(section, "$.data.id");
        String lesson = mockMvc.perform(asJson(admin, post("/api/v1/courses/" + slug + "/sections/" + sectionId + "/lessons"),
                        Map.of("title", "Commits", "type", "article", "durationMinutes", 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(lesson, "$.data.id");
        mockMvc.perform(asJson(admin, put("/api/v1/instructor/lessons/" + id + "/assistant-source"), Map.of("body", STUDY_TEXT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characters").value(STUDY_TEXT.length()));
        return new Lesson(slug, id);
    }
}
