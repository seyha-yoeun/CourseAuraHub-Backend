package com.course_aura_hub.assistant.service;

import com.course_aura_hub.assistant.config.AssistantProperties;
import com.course_aura_hub.assistant.dto.AssistantDtos;
import com.course_aura_hub.auth.config.RateLimitProperties;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.curriculum.service.LessonAvailability;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.quiz.dto.QuizAdminRequest;
import com.course_aura_hub.settings.service.SettingsService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class AssistantServiceImpl implements AssistantService {

    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(3).toMillis();
    private static final int MAX_TRACKED = 10_000;
    private static final long SUMMARY_TOKENS = 2_000L;

    private final ClaudeGateway claude;
    private final LessonRepository lessonRepository;
    private final CourseAccess courseAccess;
    private final LessonAvailability lessonAvailability;
    private final LearnerAccountService learnerAccountService;
    private final StudentCourseRepository studentCourseRepository;
    private final SettingsService settingsService;
    private final JdbcTemplate jdbcTemplate;
    private final AssistantProperties properties;
    private final RateLimitProperties rateLimitProperties;
    private final TransactionTemplate transaction;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    // Answers stream on their own threads, so a slow model never holds a request thread. Deliberately not a bean:
    // an Executor bean would replace Spring Boot's default task executor
    private final ExecutorService executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100));

    public AssistantServiceImpl(ClaudeGateway claude, LessonRepository lessonRepository, CourseAccess courseAccess,
                                LessonAvailability lessonAvailability, LearnerAccountService learnerAccountService,
                                StudentCourseRepository studentCourseRepository, SettingsService settingsService,
                                JdbcTemplate jdbcTemplate, AssistantProperties properties,
                                RateLimitProperties rateLimitProperties, PlatformTransactionManager transactionManager) {
        this.claude = claude;
        this.lessonRepository = lessonRepository;
        this.courseAccess = courseAccess;
        this.lessonAvailability = lessonAvailability;
        this.learnerAccountService = learnerAccountService;
        this.studentCourseRepository = studentCourseRepository;
        this.settingsService = settingsService;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.rateLimitProperties = rateLimitProperties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public AssistantDtos.Source saveSource(Actor actor, Integer lessonId, String body) {
        if (body.length() > properties.maxSourceChars()) {
            throw new BusinessRuleException("The text must not exceed " + properties.maxSourceChars() + " characters");
        }
        return transaction.execute(status -> {
            Lesson lesson = lesson(lessonId);
            courseAccess.requireManager(lesson.getSection().getCourse(), actor);
            jdbcTemplate.update("""
                    insert into lesson_ai_sources (lesson_id, body, updated_by) values (?, ?, ?)
                    on conflict (lesson_id) do update set body = excluded.body, updated_by = excluded.updated_by,
                        updated_at = now()
                    """, lessonId, body.strip(), actor.userId());
            return jdbcTemplate.queryForObject("select length(body), updated_at from lesson_ai_sources where lesson_id = ?",
                    (rs, i) -> new AssistantDtos.Source(lessonId, rs.getInt(1), rs.getTimestamp(2).toInstant()), lessonId);
        });
    }

    /** Everything the worker thread needs, read and checked before the model is called. */
    private record Prepared(Integer studentId, long conversationId, String system, List<ClaudeGateway.Turn> turns) {
    }

    @Override
    public SseEmitter ask(Actor actor, Integer lessonId, AssistantDtos.AskRequest request) {
        if (!claude.available()) {
            throw new ServiceUnavailableException("The study assistant is not available right now");
        }
        if (!allow(actor.userId().toString())) {
            throw new BusinessRuleException("You're asking very quickly. Wait a minute and try again.");
        }
        Prepared prepared = transaction.execute(status -> prepare(actor, lessonId, request));

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        try {
            executor.execute(() -> answer(prepared, emitter));
        } catch (RejectedExecutionException ex) {
            throw new ServiceUnavailableException("The study assistant is busy. Please try again shortly.");
        }
        return emitter;
    }

    private Prepared prepare(Actor actor, Integer lessonId, AssistantDtos.AskRequest request) {
        Lesson lesson = lesson(lessonId);
        Student student = learnerWithAccess(actor, lesson);
        long usedToday = tokensToday(student.getId());
        if (usedToday >= properties.dailyTokenLimit()) {
            throw new BusinessRuleException("You've reached today's limit for the study assistant. It resets at midnight UTC.");
        }

        long conversationId;
        if (request.conversationId() == null) {
            conversationId = jdbcTemplate.queryForObject("""
                    insert into assistant_conversations (student_id, lesson_id) values (?, ?) returning id
                    """, Long.class, student.getId(), lessonId);
        } else {
            // Someone else's conversation, or one about another lesson, reads as missing
            List<Long> owned = jdbcTemplate.queryForList("""
                    select id from assistant_conversations where id = ? and student_id = ? and lesson_id = ?
                    """, Long.class, request.conversationId(), student.getId(), lessonId);
            if (owned.isEmpty()) {
                throw new ResourceNotFoundException("Conversation not found");
            }
            conversationId = owned.getFirst();
        }

        List<ClaudeGateway.Turn> turns = new ArrayList<>(jdbcTemplate.query("""
                select role, content from (
                    select role, content, id from assistant_messages where conversation_id = ?
                    order by id desc limit ?) recent
                order by id
                """, (rs, i) -> new ClaudeGateway.Turn("USER".equals(rs.getString("role")), rs.getString("content")),
                conversationId, properties.historyMessages()));
        // The model expects turns to alternate, starting with the learner
        while (!turns.isEmpty() && !turns.getFirst().fromUser()) {
            turns.removeFirst();
        }
        String question = request.message().strip();
        turns.add(new ClaudeGateway.Turn(true, question));
        jdbcTemplate.update("insert into assistant_messages (conversation_id, role, content) values (?, 'USER', ?)",
                conversationId, question);
        return new Prepared(student.getId(), conversationId, systemPrompt(lesson), turns);
    }

    private void answer(Prepared prepared, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("conversation").data(Map.of("conversationId", prepared.conversationId())));
            AtomicBoolean gone = new AtomicBoolean(false);
            ClaudeGateway.Answer answer = claude.streamAnswer(prepared.system(), prepared.turns(), text -> {
                if (gone.get()) {
                    return;
                }
                try {
                    emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
                } catch (IOException | IllegalStateException ex) {
                    // The browser went away; the answer still finishes and is saved for next time
                    gone.set(true);
                }
            });
            String text = answer.refused()
                    ? "I can't help with that one. Try asking about the lesson in a different way."
                    : answer.text();
            Long messageId = transaction.execute(status -> {
                record(prepared.studentId(), answer.inputTokens() + answer.outputTokens());
                return jdbcTemplate.queryForObject("""
                        insert into assistant_messages (conversation_id, role, content, input_tokens, output_tokens)
                        values (?, 'ASSISTANT', ?, ?, ?) returning id
                        """, Long.class, prepared.conversationId(), text, answer.inputTokens(), answer.outputTokens());
            });
            if (answer.refused()) {
                emitter.send(SseEmitter.event().name("delta").data(Map.of("text", text)));
            }
            emitter.send(SseEmitter.event().name("done").data(Map.of("messageId", messageId, "refused", answer.refused())));
            emitter.complete();
        } catch (RuntimeException | IOException ex) {
            log.warn("Assistant answer failed conversationId={}: {}", prepared.conversationId(), ex.getMessage());
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message",
                        "The study assistant couldn't answer. Please try again.")));
                emitter.complete();
            } catch (IOException | IllegalStateException ignored) {
                emitter.completeWithError(ex);
            }
        }
    }

    @Override
    public List<AssistantDtos.Conversation> conversations(Actor actor, Integer lessonId) {
        return transaction.execute(status -> {
            Student student = learnerWithAccess(actor, lesson(lessonId));
            record Row(long id, Instant createdAt) {
            }
            return jdbcTemplate.query("""
                    select id, created_at from assistant_conversations where student_id = ? and lesson_id = ?
                    order by created_at desc, id desc limit 5
                    """, (rs, i) -> new Row(rs.getLong("id"), rs.getTimestamp("created_at").toInstant()),
                    student.getId(), lessonId).stream()
                    .map(row -> new AssistantDtos.Conversation(row.id(), lessonId, row.createdAt(),
                            jdbcTemplate.query("""
                                    select id, role, content, created_at from assistant_messages
                                    where conversation_id = ? order by id
                                    """, (rs, i) -> new AssistantDtos.Message(rs.getLong("id"),
                                    rs.getString("role").toLowerCase(Locale.ROOT), rs.getString("content"),
                                    rs.getTimestamp("created_at").toInstant()), row.id())))
                    .toList();
        });
    }

    @Override
    public AssistantDtos.Usage usage(Actor actor) {
        Integer studentId = learnerAccountService.findCurrentStudent(actor.email()).map(Student::getId).orElse(null);
        long used = studentId == null ? 0 : tokensToday(studentId);
        return new AssistantDtos.Usage(used, Math.max(0, properties.dailyTokenLimit() - used));
    }

    @Override
    public AssistantDtos.Summary summary(Actor actor, Integer lessonId) {
        record Context(String system, String hash, String cached) {
        }
        Context context = transaction.execute(status -> {
            Lesson lesson = lesson(lessonId);
            if (!lesson.isPreview() && !courseAccess.canManage(lesson.getSection().getCourse(), actor)) {
                learnerWithAccess(actor, lesson);
            }
            String system = systemPrompt(lesson);
            String hash = sha256(system);
            List<String> cached = jdbcTemplate.queryForList(
                    "select summary from lesson_ai_summaries where lesson_id = ? and source_hash = ?", String.class,
                    lessonId, hash);
            return new Context(system, hash, cached.isEmpty() ? null : cached.getFirst());
        });
        if (context.cached() != null) {
            return new AssistantDtos.Summary(lessonId, context.cached());
        }
        if (!claude.available()) {
            throw new ServiceUnavailableException("The study assistant is not available right now");
        }
        ClaudeGateway.Answer answer = claude.complete(context.system(),
                "Summarize this lesson for a learner in 5 to 8 short bullet points, then list its key terms.",
                SUMMARY_TOKENS);
        if (answer.refused() || answer.text().isBlank()) {
            throw new ServiceUnavailableException("A summary isn't available for this lesson");
        }
        jdbcTemplate.update("""
                insert into lesson_ai_summaries (lesson_id, source_hash, summary) values (?, ?, ?)
                on conflict (lesson_id) do update set source_hash = excluded.source_hash, summary = excluded.summary,
                    created_at = now()
                """, lessonId, context.hash(), answer.text());
        return new AssistantDtos.Summary(lessonId, answer.text());
    }

    @Override
    public List<QuizAdminRequest.Question> draftQuiz(Actor actor, Integer lessonId, int count) {
        String system = transaction.execute(status -> {
            Lesson lesson = lesson(lessonId);
            courseAccess.requireManager(lesson.getSection().getCourse(), actor);
            if (source(lessonId) == null) {
                throw new BusinessRuleException("Add the lesson's study text first, so questions come from the lesson");
            }
            return systemPrompt(lesson);
        });
        if (!claude.available()) {
            throw new ServiceUnavailableException("The study assistant is not available right now");
        }
        QuizDraftOutput draft = claude.draftQuiz(system, ("Write %d multiple-choice questions that check understanding "
                + "of this lesson. Each has 3 to 5 options with exactly one correct, and a short explanation. "
                + "Use only facts from the lesson text.").formatted(count));
        if (draft == null || draft.questions == null) {
            throw new ServiceUnavailableException("No questions could be drafted for this lesson");
        }
        // Only drafts the quiz editor would accept: 2 to 8 options with exactly one correct
        return draft.questions.stream()
                .filter(question -> question.prompt != null && question.options != null)
                .filter(question -> question.options.size() >= 2 && question.options.size() <= 8)
                .filter(question -> question.options.stream().filter(option -> option.correct).count() == 1)
                .limit(count)
                .map(question -> new QuizAdminRequest.Question(question.prompt.strip(), question.explanation,
                        question.options.stream()
                                .map(option -> new QuizAdminRequest.Option(option.text, option.correct))
                                .toList()))
                .toList();
    }

    /**
     * Stable for a given lesson text, so it is cached across questions. Holds no learner details, and no quiz
     * questions or answers: the model can't give away what it never sees.
     */
    private String systemPrompt(Lesson lesson) {
        String source = source(lesson.getId());
        String platform = settingsService.getPublicSettings().platformName();
        return """
                You are the study assistant for the lesson "%s" in the course "%s" on %s.

                Answer the learner's questions from the lesson text below. When the answer isn't in it, say so in \
                one sentence, then help with general knowledge and make clear that part goes beyond the lesson.
                Help the learner understand rather than doing graded work for them: for quiz, assignment or \
                exercise questions, explain the idea and guide them towards the answer instead of stating it.
                Keep answers short and clear. Reply in the language the learner writes in, such as Khmer.

                <lesson_text>
                %s
                </lesson_text>
                """.formatted(lesson.getTitle(), lesson.getSection().getCourse().getTitle(), platform,
                source == null ? "(This lesson has no study text yet. Answer from general knowledge and say so.)" : source);
    }

    private String source(Integer lessonId) {
        List<String> body = jdbcTemplate.queryForList("select body from lesson_ai_sources where lesson_id = ?",
                String.class, lessonId);
        return body.isEmpty() ? null : body.getFirst();
    }

    /** Enrolled learners the lesson has opened for. */
    private Student learnerWithAccess(Actor actor, Lesson lesson) {
        Student student = learnerAccountService.findCurrentStudent(actor.email())
                .filter(found -> studentCourseRepository.existsByStudentIdAndCourseId(
                        found.getId(), lesson.getSection().getCourse().getId()))
                .orElseThrow(() -> new ForbiddenActionException("Enroll in this course to use the study assistant"));
        lessonAvailability.requireAvailable(lesson, student.getId());
        return student;
    }

    private Lesson lesson(Integer lessonId) {
        return lessonRepository.findDetailedById(lessonId).orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
    }

    private long tokensToday(Integer studentId) {
        List<Long> tokens = jdbcTemplate.queryForList(
                "select tokens from assistant_usage where student_id = ? and usage_date = ?", Long.class,
                studentId, LocalDate.now(ZoneOffset.UTC));
        return tokens.isEmpty() ? 0 : tokens.getFirst();
    }

    private void record(Integer studentId, long tokens) {
        jdbcTemplate.update("""
                insert into assistant_usage (student_id, usage_date, requests, tokens) values (?, ?, 1, ?)
                on conflict (student_id, usage_date) do update set requests = assistant_usage.requests + 1,
                    tokens = assistant_usage.tokens + excluded.tokens
                """, studentId, LocalDate.now(ZoneOffset.UTC), tokens);
    }

    private boolean allow(String key) {
        if (!rateLimitProperties.enabled()) {
            return true;
        }
        Bucket bucket;
        synchronized (buckets) {
            bucket = buckets.computeIfAbsent(key, ignored -> Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(properties.requestsPerMinute())
                            .refillGreedy(properties.requestsPerMinute(), Duration.ofMinutes(1))
                            .build())
                    .build());
        }
        return bucket.tryConsume(1);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
