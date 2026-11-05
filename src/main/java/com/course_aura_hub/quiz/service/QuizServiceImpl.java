package com.course_aura_hub.quiz.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.curriculum.dto.LessonProgressResponse;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.model.LessonType;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.curriculum.service.LessonProgressService;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.quiz.dto.QuizAdminRequest;
import com.course_aura_hub.quiz.dto.QuizAdminResponse;
import com.course_aura_hub.quiz.dto.QuizAttemptGrantRequest;
import com.course_aura_hub.quiz.dto.QuizAttemptGrantResponse;
import com.course_aura_hub.quiz.dto.QuizResponse;
import com.course_aura_hub.quiz.dto.QuizResultResponse;
import com.course_aura_hub.quiz.dto.QuizRules;
import com.course_aura_hub.quiz.model.QuizAttempt;
import com.course_aura_hub.quiz.model.QuizAttemptGrant;
import com.course_aura_hub.quiz.model.QuizOption;
import com.course_aura_hub.quiz.model.QuizQuestion;
import com.course_aura_hub.quiz.model.QuizSession;
import com.course_aura_hub.quiz.repository.QuizAttemptGrantRepository;
import com.course_aura_hub.quiz.repository.QuizAttemptRepository;
import com.course_aura_hub.quiz.repository.QuizQuestionRepository;
import com.course_aura_hub.quiz.repository.QuizSessionRepository;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import com.course_aura_hub.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    /** Network delay allowed after a timed quiz's deadline. */
    static final Duration SUBMIT_GRACE = Duration.ofSeconds(30);

    private static final Random RANDOM = new SecureRandom();

    private final QuizQuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizSessionRepository sessionRepository;
    private final QuizAttemptGrantRepository grantRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressService lessonProgressService;
    private final StudentCourseRepository studentCourseRepository;
    private final StudentRepository studentRepository;
    private final LearnerAccountService learnerAccountService;

    /** Attempts left and when the next one may start, for one learner on one quiz. */
    private record AttemptState(long used, Integer remaining, Instant nextAttemptAt) {

        void requireCanAttempt() {
            if (remaining != null && remaining <= 0) {
                throw new BusinessRuleException("You have no attempts left for this quiz");
            }
            if (nextAttemptAt != null) {
                throw new BusinessRuleException("You can try this quiz again at " + nextAttemptAt);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public QuizResponse findForLearner(String email, Integer lessonId) {
        Lesson lesson = findQuizLesson(lessonId);
        Student student = requireEnrollment(email, lesson);

        if (!lesson.quizNeedsSession()) {
            return toLearnerResponse(lesson, student, questionRepository.findByLessonIdOrderByPositionAsc(lessonId),
                    null, false, null);
        }
        Optional<QuizSession> open = sessionRepository.findByStudentIdAndLessonIdAndSubmittedAtIsNull(student.getId(), lessonId)
                .filter(session -> !session.isExpired(Instant.now()));
        if (open.isPresent()) {
            List<QuizQuestion> questions = sessionQuestions(open.get());
            if (!questions.isEmpty()) {
                return toLearnerResponse(lesson, student, questions, open.get(), false, open.get().getExpiresAt());
            }
        }
        return toLearnerResponse(lesson, student, List.of(), null, true, null);
    }

    @Override
    @Transactional
    public QuizResponse start(String email, Integer lessonId) {
        Lesson lesson = findQuizLesson(lessonId);
        Student student = lockStudent(requireEnrollment(email, lesson));
        if (!lesson.quizNeedsSession()) {
            return findForLearner(email, lessonId);
        }

        Optional<QuizSession> open = sessionRepository.findOpenForUpdate(student.getId(), lessonId);
        if (open.isPresent() && !open.get().isExpired(Instant.now())) {
            QuizSession session = open.get();
            List<QuizQuestion> questions = sessionQuestions(session);
            if (!questions.isEmpty()) {
                return toLearnerResponse(lesson, student, questions, session, false, session.getExpiresAt());
            }
            // Every question it drew was edited away: close it without counting an attempt
            session.setSubmittedAt(Instant.now());
            sessionRepository.flush();
        } else {
            // A session left to run out counts as a failed attempt, so restarting can't reveal a fresh pool for free
            open.ifPresent(expired -> recordTimeout(expired, lesson));
        }

        attemptState(lesson, student.getId()).requireCanAttempt();

        List<QuizQuestion> pool = questionRepository.findByLessonIdOrderByPositionAsc(lessonId);
        if (pool.isEmpty()) {
            throw new BusinessRuleException("This quiz has no questions yet");
        }
        List<QuizQuestion> drawn = draw(pool, lesson);
        Instant now = Instant.now();
        QuizSession session = sessionRepository.save(QuizSession.builder()
                .studentId(student.getId())
                .lessonId(lessonId)
                .questionIds(QuizSession.join(drawn.stream().map(QuizQuestion::getId).toList()))
                .startedAt(now)
                .expiresAt(lesson.getQuizTimeLimitMinutes() == null ? null
                        : now.plus(Duration.ofMinutes(lesson.getQuizTimeLimitMinutes())))
                .build());
        log.info("Quiz session started lessonId={} studentId={} questions={}", lessonId, student.getId(), drawn.size());
        return toLearnerResponse(lesson, student, drawn, session, false, session.getExpiresAt());
    }

    @Override
    @Transactional
    public QuizResultResponse submit(String email, Integer lessonId, Map<Integer, Integer> answers) {
        Lesson lesson = findQuizLesson(lessonId);
        Student student = lockStudent(requireEnrollment(email, lesson));

        QuizSession session = null;
        List<QuizQuestion> questions;
        if (lesson.quizNeedsSession()) {
            session = sessionRepository.findOpenForUpdate(student.getId(), lessonId)
                    .orElseThrow(() -> new BusinessRuleException("Start the quiz before submitting"));
            questions = sessionQuestions(session);
            if (questions.isEmpty()) {
                // Starting again closes this session
                throw new BusinessRuleException("This quiz changed since you started it; start it again");
            }
        } else {
            attemptState(lesson, student.getId()).requireCanAttempt();
            questions = questionRepository.findByLessonIdOrderByPositionAsc(lessonId);
        }
        if (questions.isEmpty()) {
            throw new BusinessRuleException("This quiz has no questions yet");
        }

        Instant now = Instant.now();
        boolean timedOut = session != null && session.getExpiresAt() != null
                && now.isAfter(session.getExpiresAt().plus(SUBMIT_GRACE));
        // Untimed quizzes need every answer; a timed one counts what was answered in time
        if (session == null || session.getExpiresAt() == null) {
            if (questions.stream().anyMatch(question -> !answers.containsKey(question.getId()))) {
                throw new BusinessRuleException("Answer every question before submitting");
            }
        }

        int correctCount = timedOut ? 0 : (int) questions.stream()
                .filter(question -> isCorrect(question, answers.get(question.getId())))
                .count();
        int scorePercent = Math.round(correctCount * 100f / questions.size());
        boolean passed = scorePercent >= lesson.getQuizPassPercent();

        attemptRepository.save(QuizAttempt.builder()
                .studentId(student.getId())
                .lessonId(lessonId)
                .correctCount(correctCount)
                .totalCount(questions.size())
                .scorePercent(scorePercent)
                .passed(passed)
                .startedAt(session == null ? null : session.getStartedAt())
                .build());
        if (session != null) {
            session.setSubmittedAt(now);
        }
        log.info("Quiz attempt lessonId={} studentId={} score={} passed={} timedOut={}",
                lessonId, student.getId(), scorePercent, passed, timedOut);

        LessonProgressResponse progress = passed ? lessonProgressService.markComplete(email, lessonId) : null;
        return new QuizResultResponse(
                scorePercent,
                correctCount,
                questions.size(),
                lesson.getQuizPassPercent(),
                passed,
                questions.stream()
                        .map(question -> new QuizResultResponse.QuestionResult(
                                question.getId(),
                                !timedOut && isCorrect(question, answers.get(question.getId())),
                                passed ? correctOptionId(question) : null,
                                passed ? question.getExplanation() : null
                        ))
                        .toList(),
                progress
        );
    }

    @Override
    @Transactional(readOnly = true)
    public QuizAdminResponse findForAdmin(Integer lessonId) {
        return toAdminResponse(findQuizLesson(lessonId), questionRepository.findByLessonIdOrderByPositionAsc(lessonId));
    }

    @Override
    @Transactional
    public QuizAdminResponse save(Integer lessonId, QuizAdminRequest request) {
        Lesson lesson = findQuizLesson(lessonId);
        request.questions().forEach(question -> {
            if (question.options().stream().filter(QuizAdminRequest.Option::correct).count() != 1) {
                throw new BusinessRuleException("Each question needs exactly one correct option");
            }
        });
        if (request.questionsPerAttempt() != null && request.questionsPerAttempt() > request.questions().size()) {
            throw new BusinessRuleException("Questions per attempt can't exceed the number of questions");
        }

        // Removed first: the old rows must be gone before the new ones are numbered from 0
        questionRepository.deleteByLessonId(lessonId);
        questionRepository.flush();

        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < request.questions().size(); i++) {
            QuizAdminRequest.Question input = request.questions().get(i);
            QuizQuestion question = QuizQuestion.builder()
                    .lesson(lesson)
                    .position(i)
                    .prompt(input.prompt().trim())
                    .explanation(StringUtils.hasText(input.explanation()) ? input.explanation().trim() : null)
                    .build();
            for (int j = 0; j < input.options().size(); j++) {
                QuizAdminRequest.Option option = input.options().get(j);
                question.getOptions().add(QuizOption.builder()
                        .question(question)
                        .position(j)
                        .text(option.text().trim())
                        .correct(option.correct())
                        .build());
            }
            questions.add(question);
        }
        lesson.setQuizPassPercent(request.passPercent());
        lesson.setQuizMaxAttempts(request.maxAttempts());
        lesson.setQuizRetryCooldownMinutes(request.retryCooldownMinutes());
        lesson.setQuizTimeLimitMinutes(request.timeLimitMinutes());
        lesson.setQuizQuestionsPerAttempt(request.questionsPerAttempt());
        lesson.setQuizShuffle(Boolean.TRUE.equals(request.shuffle()));
        List<QuizQuestion> saved = questionRepository.saveAll(questions);

        log.info("Saved quiz lessonId={} questions={} rules={}", lessonId, saved.size(), QuizRules.of(lesson));
        return toAdminResponse(lesson, saved);
    }

    @Override
    @Transactional
    public QuizAttemptGrantResponse grantAttempts(Integer lessonId, QuizAttemptGrantRequest request, UUID grantedBy) {
        Lesson lesson = findQuizLesson(lessonId);
        Integer courseId = lesson.getSection().getCourse().getId();
        if (!studentCourseRepository.existsByStudentIdAndCourseId(request.studentId(), courseId)) {
            throw ResourceNotFoundException.of("Enrolled student", request.studentId());
        }
        QuizAttemptGrant.Id id = new QuizAttemptGrant.Id(request.studentId(), lessonId);
        QuizAttemptGrant grant = grantRepository.findById(id)
                .orElseGet(() -> QuizAttemptGrant.builder().id(id).extraAttempts(0).build());
        grant.setExtraAttempts(grant.getExtraAttempts() + request.extraAttempts());
        grant.setGrantedBy(grantedBy);
        grantRepository.save(grant);

        AttemptState state = attemptState(lesson, request.studentId());
        log.info("Granted quiz attempts lessonId={} studentId={} extra={}", lessonId, request.studentId(), request.extraAttempts());
        return new QuizAttemptGrantResponse(request.studentId(), lessonId, grant.getExtraAttempts(), state.used(), state.remaining());
    }

    private AttemptState attemptState(Lesson lesson, Integer studentId) {
        long used = attemptRepository.countByStudentIdAndLessonId(studentId, lesson.getId());
        Integer remaining = null;
        if (lesson.getQuizMaxAttempts() != null) {
            int extra = grantRepository.findById(new QuizAttemptGrant.Id(studentId, lesson.getId()))
                    .map(QuizAttemptGrant::getExtraAttempts)
                    .orElse(0);
            remaining = (int) Math.max(0, lesson.getQuizMaxAttempts() + extra - used);
        }
        Instant nextAttemptAt = null;
        if (lesson.getQuizRetryCooldownMinutes() != null) {
            nextAttemptAt = attemptRepository.findFirstByStudentIdAndLessonIdOrderBySubmittedAtDescIdDesc(studentId, lesson.getId())
                    .filter(last -> !last.isPassed())
                    .map(last -> last.getSubmittedAt().plus(Duration.ofMinutes(lesson.getQuizRetryCooldownMinutes())))
                    .filter(Instant.now()::isBefore)
                    .orElse(null);
        }
        return new AttemptState(used, remaining, nextAttemptAt);
    }

    private void recordTimeout(QuizSession session, Lesson lesson) {
        List<Integer> ids = session.questionIdList();
        attemptRepository.save(QuizAttempt.builder()
                .studentId(session.getStudentId())
                .lessonId(lesson.getId())
                .correctCount(0)
                .totalCount(ids.size())
                .scorePercent(0)
                .passed(false)
                .startedAt(session.getStartedAt())
                .submittedAt(session.getExpiresAt())
                .build());
        session.setSubmittedAt(Instant.now());
        sessionRepository.flush();
        log.info("Quiz session timed out lessonId={} studentId={}", lesson.getId(), session.getStudentId());
    }

    /** Draws the pool for a new attempt; without shuffling the drawn questions keep the author's order. */
    private static List<QuizQuestion> draw(List<QuizQuestion> pool, Lesson lesson) {
        List<QuizQuestion> drawn = new ArrayList<>(pool);
        Integer count = lesson.getQuizQuestionsPerAttempt();
        if (count != null && count < drawn.size()) {
            Collections.shuffle(drawn, RANDOM);
            drawn = new ArrayList<>(drawn.subList(0, count));
            if (!lesson.isQuizShuffle()) {
                drawn.sort(Comparator.comparing(QuizQuestion::getPosition));
            }
        } else if (lesson.isQuizShuffle()) {
            Collections.shuffle(drawn, RANDOM);
        }
        return drawn;
    }

    /** The session's questions in the order it showed them; questions deleted since are left out. */
    private List<QuizQuestion> sessionQuestions(QuizSession session) {
        Map<Integer, QuizQuestion> byId = questionRepository.findByLessonIdOrderByPositionAsc(session.getLessonId()).stream()
                .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
        return session.questionIdList().stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private QuizResponse toLearnerResponse(Lesson lesson, Student student, List<QuizQuestion> questions,
                                           QuizSession session, boolean startRequired, Instant expiresAt) {
        boolean shuffleOptions = lesson.isQuizShuffle() && session != null;
        AttemptState state = attemptState(lesson, student.getId());
        return new QuizResponse(
                lesson.getId(),
                lesson.getQuizPassPercent(),
                questions.stream()
                        .map(question -> new QuizResponse.Question(
                                question.getId(),
                                question.getPrompt(),
                                options(question, shuffleOptions ? session.getId() : null)))
                        .toList(),
                state.used(),
                attemptRepository.findBestScore(student.getId(), lesson.getId()),
                attemptRepository.existsByStudentIdAndLessonIdAndPassedTrue(student.getId(), lesson.getId()),
                QuizRules.of(lesson),
                state.remaining(),
                state.nextAttemptAt(),
                startRequired,
                expiresAt
        );
    }

    /** Options in author order, or in a stable per-session shuffle so reloading doesn't reorder them. */
    private static List<QuizResponse.Option> options(QuizQuestion question, Long sessionSeed) {
        List<QuizResponse.Option> options = new ArrayList<>(question.getOptions().stream()
                .map(option -> new QuizResponse.Option(option.getId(), option.getText()))
                .toList());
        if (sessionSeed != null) {
            Collections.shuffle(options, new Random(sessionSeed * 31 + question.getId()));
        }
        return options;
    }

    private Student lockStudent(Student student) {
        // Serialises a learner's quiz actions, so parallel submits can't exceed the attempt limit
        return studentRepository.findByIdForUpdate(student.getId()).orElse(student);
    }

    private boolean isCorrect(QuizQuestion question, Integer optionId) {
        return Objects.equals(correctOptionId(question), optionId);
    }

    private Integer correctOptionId(QuizQuestion question) {
        return question.getOptions().stream()
                .filter(QuizOption::isCorrect)
                .map(QuizOption::getId)
                .findFirst()
                .orElse(null);
    }

    private Lesson findQuizLesson(Integer lessonId) {
        Lesson lesson = lessonRepository.findDetailedById(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
        if (lesson.getType() != LessonType.QUIZ) {
            throw new BusinessRuleException("This lesson is not a quiz");
        }
        return lesson;
    }

    private Student requireEnrollment(String email, Lesson lesson) {
        Integer courseId = lesson.getSection().getCourse().getId();
        return learnerAccountService.findCurrentStudent(email)
                .filter(student -> studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), courseId))
                .orElseThrow(() -> new ForbiddenActionException("You must be enrolled in this course to take its quiz"));
    }

    private QuizAdminResponse toAdminResponse(Lesson lesson, List<QuizQuestion> questions) {
        return new QuizAdminResponse(
                lesson.getId(),
                lesson.getQuizPassPercent(),
                questions.stream()
                        .map(question -> new QuizAdminResponse.Question(
                                question.getId(),
                                question.getPrompt(),
                                question.getExplanation(),
                                question.getOptions().stream()
                                        .map(option -> new QuizAdminResponse.Option(option.getId(), option.getText(), option.isCorrect()))
                                        .toList()
                        ))
                        .toList(),
                QuizRules.of(lesson)
        );
    }
}
