package com.course_aura_hub.coding.service;

import com.course_aura_hub.auth.config.RateLimitProperties;
import com.course_aura_hub.coding.config.CodingProperties;
import com.course_aura_hub.coding.dto.CodingDtos;
import com.course_aura_hub.coding.model.CodeExercise;
import com.course_aura_hub.coding.model.CodeSubmission;
import com.course_aura_hub.coding.model.TestCase;
import com.course_aura_hub.coding.repository.CodeExerciseRepository;
import com.course_aura_hub.coding.repository.CodeSubmissionRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.model.LessonType;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.curriculum.service.LessonAvailability;
import com.course_aura_hub.curriculum.service.LessonProgressService;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class CodingServiceImpl implements CodingService {

    private static final int MAX_MESSAGE = 2000;
    private static final int MAX_TRACKED = 10_000;
    private static final BigDecimal DEFAULT_TIME_LIMIT = new BigDecimal("2.0");
    private static final int DEFAULT_MEMORY_MB = 128;

    private final CodeExerciseRepository exerciseRepository;
    private final CodeSubmissionRepository submissionRepository;
    private final LessonRepository lessonRepository;
    private final CourseAccess courseAccess;
    private final LessonAvailability lessonAvailability;
    private final LessonProgressService lessonProgressService;
    private final LearnerAccountService learnerAccountService;
    private final StudentCourseRepository studentCourseRepository;
    private final CodeRunner codeRunner;
    private final CodingProperties properties;
    private final RateLimitProperties rateLimitProperties;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    public CodingServiceImpl(CodeExerciseRepository exerciseRepository, CodeSubmissionRepository submissionRepository,
                             LessonRepository lessonRepository, CourseAccess courseAccess,
                             LessonAvailability lessonAvailability, LessonProgressService lessonProgressService,
                             LearnerAccountService learnerAccountService, StudentCourseRepository studentCourseRepository,
                             CodeRunner codeRunner, CodingProperties properties, RateLimitProperties rateLimitProperties,
                             PlatformTransactionManager transactionManager) {
        this.exerciseRepository = exerciseRepository;
        this.submissionRepository = submissionRepository;
        this.lessonRepository = lessonRepository;
        this.courseAccess = courseAccess;
        this.lessonAvailability = lessonAvailability;
        this.lessonProgressService = lessonProgressService;
        this.learnerAccountService = learnerAccountService;
        this.studentCourseRepository = studentCourseRepository;
        this.codeRunner = codeRunner;
        this.properties = properties;
        this.rateLimitProperties = rateLimitProperties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public CodingDtos.Exercise findForStaff(Actor actor, Integer lessonId) {
        return readOnly.execute(status -> {
            managed(actor, lessonId);
            return exerciseRepository.findById(lessonId).map(this::toStaff).orElse(null);
        });
    }

    @Override
    public CodingDtos.Exercise save(Actor actor, Integer lessonId, CodingDtos.ExerciseRequest request) {
        String language = request.language().trim().toLowerCase(Locale.ROOT);
        if (!properties.languages().containsKey(language)) {
            throw new BusinessRuleException("Language must be one of " + String.join(", ", properties.languages().keySet()));
        }
        if (request.examples().size() + request.tests().size() > properties.maxTests()) {
            throw new BusinessRuleException("An exercise can have at most " + properties.maxTests() + " tests in all");
        }
        return transaction.execute(status -> {
            managed(actor, lessonId);
            CodeExercise exercise = exerciseRepository.findById(lessonId)
                    .orElseGet(() -> CodeExercise.builder().lessonId(lessonId).build());
            exercise.setLanguage(language);
            exercise.setInstructions(StringUtils.hasText(request.instructions()) ? request.instructions() : null);
            exercise.setStarterCode(request.starterCode() == null ? "" : request.starterCode());
            exercise.setExamples(toCases(request.examples()));
            exercise.setTests(toCases(request.tests()));
            exercise.setTimeLimitSeconds(request.timeLimitSeconds() == null ? DEFAULT_TIME_LIMIT : request.timeLimitSeconds());
            exercise.setMemoryLimitMb(request.memoryLimitMb() == null ? DEFAULT_MEMORY_MB : request.memoryLimitMb());
            return toStaff(exerciseRepository.save(exercise));
        });
    }

    @Override
    public CodingDtos.LearnerExercise findForLearner(Actor actor, Integer lessonId) {
        return readOnly.execute(status -> {
            Viewer viewer = viewer(actor, lessonId, false);
            CodeExercise exercise = exerciseRepository.findById(lessonId)
                    .orElseThrow(() -> new ResourceNotFoundException("This lesson has no exercise yet"));
            CodingDtos.LastSubmission last = viewer.studentId() == null ? null : submissionRepository
                    .findFirstByStudentIdAndLessonIdOrderByCreatedAtDescIdDesc(viewer.studentId(), lessonId)
                    .map(submission -> new CodingDtos.LastSubmission(submission.getCode(), submission.isPassed(),
                            submission.getPassedTests(), submission.getTotalTests(),
                            String.valueOf(submission.getResult().getOrDefault("verdict", "")), submission.getCreatedAt()))
                    .orElse(null);
            boolean passed = viewer.studentId() != null
                    && submissionRepository.existsByStudentIdAndLessonIdAndPassedTrue(viewer.studentId(), lessonId);
            return new CodingDtos.LearnerExercise(lessonId, exercise.getLanguage(), exercise.getInstructions(),
                    exercise.getStarterCode(), toDtos(exercise.getExamples()), exercise.getTests().size(),
                    exercise.getTimeLimitSeconds(), exercise.getMemoryLimitMb(), passed, last, properties.isConfigured());
        });
    }

    @Override
    public CodingDtos.RunResult run(Actor actor, Integer lessonId, String code) {
        if (code.getBytes(StandardCharsets.UTF_8).length > properties.maxCodeBytes()) {
            throw new BusinessRuleException("Code is too long");
        }
        // Access and the exercise are read first; the sandbox is called with no transaction open
        record Prepared(Viewer viewer, CodeExercise exercise) {
        }
        Prepared prepared = readOnly.execute(status -> {
            Viewer viewer = viewer(actor, lessonId, true);
            CodeExercise exercise = exerciseRepository.findById(lessonId)
                    .orElseThrow(() -> new ResourceNotFoundException("This lesson has no exercise yet"));
            return new Prepared(viewer, exercise);
        });
        if (!allow(actor.userId().toString())) {
            throw new BusinessRuleException("You're running code too often. Wait a minute and try again.");
        }

        CodingDtos.RunResult result = grade(prepared.exercise(), code);
        Viewer viewer = prepared.viewer();
        if (viewer.studentId() == null) {
            return result;
        }
        boolean completed = Boolean.TRUE.equals(transaction.execute(status -> {
            submissionRepository.save(CodeSubmission.builder()
                    .studentId(viewer.studentId())
                    .lessonId(lessonId)
                    .language(prepared.exercise().getLanguage())
                    .code(code)
                    .passed(result.passed())
                    .passedTests(result.passedTests())
                    .totalTests(result.totalTests())
                    .result(Map.of("verdict", result.verdict(), "message", result.message() == null ? "" : result.message()))
                    .build());
            if (result.passed()) {
                lessonProgressService.completeForStudent(viewer.studentId(), lessonId);
                return true;
            }
            return false;
        }));
        return new CodingDtos.RunResult(result.passed(), result.passedTests(), result.totalTests(), result.verdict(),
                result.message(), result.examples(), completed);
    }

    /** Examples first, shown in full; then hidden tests, stopping at the first that fails and revealing nothing. */
    private CodingDtos.RunResult grade(CodeExercise exercise, String code) {
        int languageId = properties.languages().get(exercise.getLanguage());
        int total = exercise.getExamples().size() + exercise.getTests().size();
        int passed = 0;
        List<CodingDtos.ExampleResult> examples = new ArrayList<>();
        String verdict = "accepted";
        String message = null;

        for (TestCase example : exercise.getExamples()) {
            CodeRunner.RunOutcome outcome = codeRunner.run(languageId, code, example.input(),
                    exercise.getTimeLimitSeconds(), exercise.getMemoryLimitMb());
            boolean ok = "accepted".equals(outcome.status()) && sameOutput(outcome.stdout(), example.expectedOutput());
            examples.add(new CodingDtos.ExampleResult(example.input(), example.expectedOutput(),
                    truncate(outcome.stdout()), ok));
            if (ok) {
                passed++;
            } else if (message == null) {
                verdict = failure(outcome);
                message = switch (outcome.status()) {
                    case "compile_error" -> truncate(outcome.compileOutput());
                    case "runtime_error" -> truncate(outcome.stderr());
                    case "time_limit" -> "Took longer than " + exercise.getTimeLimitSeconds() + " seconds";
                    case "accepted" -> "Your output doesn't match the expected output";
                    default -> "The code could not be run";
                };
                if ("compile_error".equals(outcome.status())) {
                    // Nothing else can pass if it doesn't compile
                    return new CodingDtos.RunResult(false, 0, total, verdict, message, examples, false);
                }
            }
        }
        if (message != null) {
            return new CodingDtos.RunResult(false, passed, total, verdict, message, examples, false);
        }

        int number = 0;
        for (TestCase test : exercise.getTests()) {
            number++;
            CodeRunner.RunOutcome outcome = codeRunner.run(languageId, code, test.input(),
                    exercise.getTimeLimitSeconds(), exercise.getMemoryLimitMb());
            if ("accepted".equals(outcome.status()) && sameOutput(outcome.stdout(), test.expectedOutput())) {
                passed++;
                continue;
            }
            // The program's own output could echo the hidden input, so none of it is returned
            verdict = failure(outcome);
            message = switch (verdict) {
                case "compile_error" -> truncate(outcome.compileOutput());
                case "time_limit" -> "Hidden test " + number + " took too long";
                case "runtime_error" -> "Hidden test " + number + " crashed";
                case "error" -> "The code could not be run";
                default -> "Hidden test " + number + " gave a different answer";
            };
            return new CodingDtos.RunResult(false, passed, total, verdict, message, examples, false);
        }
        return new CodingDtos.RunResult(true, passed, total, "accepted", null, examples, false);
    }

    private static String failure(CodeRunner.RunOutcome outcome) {
        return "accepted".equals(outcome.status()) ? "wrong_answer" : outcome.status();
    }

    /** Trailing spaces on each line and trailing blank lines don't count. */
    static boolean sameOutput(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").lines().map(String::stripTrailing)
                .reduce((a, b) -> a + "\n" + b).orElse("").stripTrailing();
    }

    private record Viewer(Integer studentId) {
    }

    /**
     * Course staff see and run without a learner record. Learners must be enrolled and the lesson open for them;
     * free previews can be read but not run.
     */
    private Viewer viewer(Actor actor, Integer lessonId, boolean running) {
        Lesson lesson = codeLesson(lessonId);
        if (courseAccess.canManage(lesson.getSection().getCourse(), actor)) {
            return new Viewer(null);
        }
        Student student = learnerAccountService.findCurrentStudent(actor.email())
                .filter(found -> studentCourseRepository.existsByStudentIdAndCourseId(
                        found.getId(), lesson.getSection().getCourse().getId()))
                .orElse(null);
        if (student == null) {
            if (lesson.isPreview() && !running) {
                return new Viewer(null);
            }
            throw new ForbiddenActionException("Enroll in this course to try this exercise");
        }
        lessonAvailability.requireAvailable(lesson, student.getId());
        return new Viewer(student.getId());
    }

    private Lesson managed(Actor actor, Integer lessonId) {
        Lesson lesson = codeLesson(lessonId);
        courseAccess.requireManager(lesson.getSection().getCourse(), actor);
        return lesson;
    }

    private Lesson codeLesson(Integer lessonId) {
        Lesson lesson = lessonRepository.findDetailedById(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
        if (lesson.getType() != LessonType.CODE) {
            throw new BusinessRuleException("This lesson is not a coding exercise");
        }
        return lesson;
    }

    private boolean allow(String key) {
        if (!rateLimitProperties.enabled()) {
            return true;
        }
        Bucket bucket;
        synchronized (buckets) {
            bucket = buckets.computeIfAbsent(key, ignored -> Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(properties.runsPerMinute())
                            .refillGreedy(properties.runsPerMinute(), Duration.ofMinutes(1))
                            .build())
                    .build());
        }
        return bucket.tryConsume(1);
    }

    private CodingDtos.Exercise toStaff(CodeExercise exercise) {
        return new CodingDtos.Exercise(exercise.getLessonId(), exercise.getLanguage(), exercise.getInstructions(),
                exercise.getStarterCode(), toDtos(exercise.getExamples()), toDtos(exercise.getTests()),
                exercise.getTimeLimitSeconds(), exercise.getMemoryLimitMb(), List.copyOf(properties.languages().keySet()));
    }

    private static List<TestCase> toCases(List<CodingDtos.Case> cases) {
        return cases.stream().map(c -> new TestCase(c.input() == null ? "" : c.input(), c.expectedOutput())).toList();
    }

    private static List<CodingDtos.Case> toDtos(List<TestCase> cases) {
        return cases.stream().map(c -> new CodingDtos.Case(c.input(), c.expectedOutput())).toList();
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_MESSAGE ? text : text.substring(0, MAX_MESSAGE) + "…";
    }
}
