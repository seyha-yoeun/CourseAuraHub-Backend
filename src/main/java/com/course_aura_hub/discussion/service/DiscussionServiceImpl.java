package com.course_aura_hub.discussion.service;

import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.discussion.dto.LessonAnswerResponse;
import com.course_aura_hub.discussion.dto.LessonQuestionResponse;
import com.course_aura_hub.discussion.dto.OpenQuestionPageResponse;
import com.course_aura_hub.discussion.dto.OpenQuestionResponse;
import com.course_aura_hub.discussion.model.LessonAnswer;
import com.course_aura_hub.discussion.model.LessonQuestion;
import com.course_aura_hub.discussion.repository.LessonAnswerRepository;
import com.course_aura_hub.discussion.repository.LessonQuestionRepository;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/** Open to enrolled learners and to the staff who manage the course. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscussionServiceImpl implements DiscussionService {

    private static final int MAX_PAGE_SIZE = 50;

    private final LessonQuestionRepository questionRepository;
    private final LessonAnswerRepository answerRepository;
    private final LessonRepository lessonRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final UserRepository userRepository;
    private final CourseAccess courseAccess;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public List<LessonQuestionResponse> findQuestions(Actor actor, Integer lessonId) {
        Lesson lesson = findLesson(lessonId);
        Course course = lesson.getSection().getCourse();
        requireAccess(actor, course);
        return questionRepository.findByLessonIdAndHiddenFalseOrderByCreatedAtDescIdDesc(lessonId).stream()
                .map(question -> toResponse(question, course, actor))
                .toList();
    }

    @Override
    @Transactional
    public LessonQuestionResponse ask(Actor actor, Integer lessonId, String body) {
        Lesson lesson = findLesson(lessonId);
        Course course = lesson.getSection().getCourse();
        requireAccess(actor, course);

        LessonQuestion question = questionRepository.save(LessonQuestion.builder()
                .lesson(lesson)
                .authorId(actor.userId())
                .authorName(displayName(actor))
                .body(body.trim())
                .build());

        String message = "%s asked about %s.".formatted(question.getAuthorName(), lesson.getTitle());
        if (course.getOwnerId() == null) {
            notificationService.notifyAdmins(NotificationType.LESSON_QUESTION, "New lesson question", message, "/questions");
        } else if (!course.getOwnerId().equals(actor.userId())) {
            notificationService.notifyUsers(
                    List.of(course.getOwnerId()), NotificationType.LESSON_QUESTION, "New lesson question", message, "/questions");
        }
        return toResponse(question, course, actor);
    }

    @Override
    @Transactional
    public LessonAnswerResponse answer(Actor actor, Integer questionId, String body) {
        LessonQuestion question = findQuestion(questionId);
        Course course = question.getLesson().getSection().getCourse();
        requireAccess(actor, course);

        LessonAnswer answer = answerRepository.save(LessonAnswer.builder()
                .question(question)
                .authorId(actor.userId())
                .authorName(displayName(actor))
                .fromStaff(courseAccess.canManage(course, actor))
                .body(body.trim())
                .build());

        if (question.getAuthorId() != null && !question.getAuthorId().equals(actor.userId())) {
            notificationService.notifyUsers(
                    List.of(question.getAuthorId()),
                    NotificationType.LESSON_QUESTION,
                    "Your question has a reply",
                    "%s replied about %s.".formatted(answer.getAuthorName(), question.getLesson().getTitle()),
                    "/learn/%s?lesson=%d".formatted(course.getSlug(), question.getLesson().getId())
            );
        }
        return toResponse(answer, course, actor);
    }

    @Override
    @Transactional
    public LessonQuestionResponse setResolved(Actor actor, Integer questionId, boolean resolved) {
        LessonQuestion question = findQuestion(questionId);
        Course course = question.getLesson().getSection().getCourse();
        requireModerator(actor, course, question.getAuthorId());
        question.setResolved(resolved);
        return toResponse(question, course, actor);
    }

    @Override
    @Transactional
    public void deleteQuestion(Actor actor, Integer questionId) {
        LessonQuestion question = findQuestion(questionId);
        requireModerator(actor, question.getLesson().getSection().getCourse(), question.getAuthorId());
        questionRepository.delete(question);
        log.info("Deleted lesson question id={} by user={}", questionId, actor.userId());
    }

    @Override
    @Transactional
    public void deleteAnswer(Actor actor, Integer answerId) {
        LessonAnswer answer = answerRepository.findDetailedById(answerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Answer", answerId));
        requireModerator(actor, answer.getQuestion().getLesson().getSection().getCourse(), answer.getAuthorId());
        // Through the parent: orphan removal would otherwise bring the answer back on flush
        answer.getQuestion().getAnswers().remove(answer);
        log.info("Deleted lesson answer id={} by user={}", answerId, actor.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public OpenQuestionPageResponse findOpen(Actor actor, int page, int size) {
        PageRequest request = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
        Page<LessonQuestion> questions = actor.admin()
                ? questionRepository.findByResolvedFalseAndHiddenFalse(request)
                : questionRepository.findOpenByCourseOwner(actor.userId(), request);
        return new OpenQuestionPageResponse(
                questions.map(this::toOpenResponse).getContent(),
                questions.getNumber(),
                questions.getSize(),
                questions.getTotalElements(),
                questions.getTotalPages()
        );
    }

    private Lesson findLesson(Integer lessonId) {
        return lessonRepository.findDetailedById(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
    }

    private LessonQuestion findQuestion(Integer questionId) {
        return questionRepository.findDetailedById(questionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Question", questionId));
    }

    private void requireAccess(Actor actor, Course course) {
        if (!courseAccess.canManage(course, actor) && !studentCourseRepository.isUserEnrolled(actor.email(), course.getId())) {
            throw new ForbiddenActionException("Enroll in this course to join its discussion");
        }
    }

    private void requireModerator(Actor actor, Course course, java.util.UUID authorId) {
        if (!Objects.equals(authorId, actor.userId()) && !courseAccess.canManage(course, actor)) {
            throw new ForbiddenActionException("You can only change your own posts");
        }
    }

    private String displayName(Actor actor) {
        return userRepository.findById(actor.userId())
                .map(this::displayName)
                .orElseThrow(() -> ResourceNotFoundException.of("User", actor.userId()));
    }

    private String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return StringUtils.hasText(name) ? name : "Learner";
    }

    private LessonQuestionResponse toResponse(LessonQuestion question, Course course, Actor actor) {
        return new LessonQuestionResponse(
                question.getId(),
                question.getLesson().getId(),
                question.getAuthorName(),
                question.getBody(),
                question.isResolved(),
                question.getCreatedAt(),
                Objects.equals(question.getAuthorId(), actor.userId()) || courseAccess.canManage(course, actor),
                question.getAnswers().stream().filter(answer -> !answer.isHidden())
                        .map(answer -> toResponse(answer, course, actor)).toList()
        );
    }

    private LessonAnswerResponse toResponse(LessonAnswer answer, Course course, Actor actor) {
        return new LessonAnswerResponse(
                answer.getId(),
                answer.getAuthorName(),
                answer.isFromStaff(),
                answer.getBody(),
                answer.getCreatedAt(),
                Objects.equals(answer.getAuthorId(), actor.userId()) || courseAccess.canManage(course, actor)
        );
    }

    private OpenQuestionResponse toOpenResponse(LessonQuestion question) {
        Lesson lesson = question.getLesson();
        Course course = lesson.getSection().getCourse();
        return new OpenQuestionResponse(
                question.getId(),
                lesson.getId(),
                lesson.getTitle(),
                course.getSlug(),
                course.getTitle(),
                question.getAuthorName(),
                question.getBody(),
                (int) question.getAnswers().stream().filter(answer -> !answer.isHidden()).count(),
                question.getCreatedAt()
        );
    }
}
