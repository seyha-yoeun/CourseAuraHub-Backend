package com.course_aura_hub.assignment.service;

import com.course_aura_hub.assignment.dto.AssignmentGradeRequest;
import com.course_aura_hub.assignment.dto.AssignmentRequest;
import com.course_aura_hub.assignment.dto.AssignmentResponse;
import com.course_aura_hub.assignment.dto.AssignmentSubmitRequest;
import com.course_aura_hub.assignment.dto.SubmissionPageResponse;
import com.course_aura_hub.assignment.dto.SubmissionResponse;
import com.course_aura_hub.assignment.dto.SubmissionReviewResponse;
import com.course_aura_hub.assignment.model.Assignment;
import com.course_aura_hub.assignment.model.AssignmentSubmission;
import com.course_aura_hub.assignment.model.SubmissionStatus;
import com.course_aura_hub.assignment.repository.AssignmentRepository;
import com.course_aura_hub.assignment.repository.AssignmentSubmissionRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.model.LessonType;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.curriculum.service.LessonAvailability;
import com.course_aura_hub.curriculum.service.LessonProgressService;
import com.course_aura_hub.file.model.FilePurpose;
import com.course_aura_hub.file.service.FileService;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressService lessonProgressService;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;
    private final LessonAvailability lessonAvailability;
    private final CourseAccess courseAccess;
    private final FileService fileService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse findForLearner(Actor actor, Integer lessonId) {
        Lesson lesson = findLesson(lessonId);
        Course course = lesson.getSection().getCourse();
        boolean staff = courseAccess.canManage(course, actor);
        if (!staff && !studentCourseRepository.isUserEnrolled(actor.email(), course.getId())) {
            throw new ForbiddenActionException("Enroll in this course to see its assignments");
        }
        if (!staff) {
            learnerAccountService.findCurrentStudent(actor.email())
                    .ifPresent(student -> lessonAvailability.requireAvailable(lesson, student.getId()));
        }
        return assignmentRepository.findByLessonId(lessonId)
                .map(assignment -> toResponse(assignment, learnerAccountService.findCurrentStudent(actor.email())
                        .flatMap(student -> submissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), student.getId()))
                        .orElse(null)))
                .orElse(null);
    }

    @Override
    @Transactional
    public AssignmentResponse submit(Actor actor, Integer lessonId, AssignmentSubmitRequest request) {
        Lesson lesson = findLesson(lessonId);
        Course course = lesson.getSection().getCourse();
        Assignment assignment = assignmentRepository.findByLessonId(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Assignment", lessonId));
        Student student = learnerAccountService.currentStudent(actor.email());
        if (!studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new ForbiddenActionException("Enroll in this course to submit work");
        }
        lessonAvailability.requireAvailable(lesson, student.getId());

        String fileUrl = StringUtils.hasText(request.fileUrl()) ? request.fileUrl().trim() : null;
        String comment = StringUtils.hasText(request.comment()) ? request.comment().trim() : null;
        if (fileUrl == null && comment == null) {
            throw new BusinessRuleException("Attach a file or write a comment");
        }
        if (fileUrl != null) {
            fileService.requireOwned(fileUrl, actor.email(), FilePurpose.ASSIGNMENT_SUBMISSION);
        }

        AssignmentSubmission submission = submissionRepository
                .findByAssignmentIdAndStudentId(assignment.getId(), student.getId())
                .orElseGet(() -> AssignmentSubmission.builder().assignment(assignment).student(student).build());
        if (submission.passed()) {
            throw new BusinessRuleException("You've already passed this assignment");
        }
        submission.setFileUrl(fileUrl);
        submission.setFileName(fileUrl == null ? null : request.fileName());
        submission.setComment(comment);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setScore(null);
        submission.setFeedback(null);
        submission.setGradedBy(null);
        submission.setGradedAt(null);
        submission.setSubmittedAt(Instant.now());
        AssignmentSubmission saved = submissionRepository.save(submission);

        String message = "%s submitted work for %s.".formatted(student.getName(), lesson.getTitle());
        if (course.getOwnerId() == null) {
            notificationService.notifyAdmins(NotificationType.ASSIGNMENT, "New submission", message, "/instructor/submissions");
        } else {
            notificationService.notifyUsers(
                    List.of(course.getOwnerId()), NotificationType.ASSIGNMENT, "New submission", message, "/instructor/submissions");
        }
        return toResponse(assignment, saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentResponse findForStaff(Actor actor, Integer lessonId) {
        Lesson lesson = findLesson(lessonId);
        courseAccess.requireManager(lesson.getSection().getCourse(), actor);
        return assignmentRepository.findByLessonId(lessonId).map(assignment -> toResponse(assignment, null)).orElse(null);
    }

    @Override
    @Transactional
    public AssignmentResponse save(Actor actor, Integer lessonId, AssignmentRequest request) {
        Lesson lesson = findLesson(lessonId);
        courseAccess.requireManager(lesson.getSection().getCourse(), actor);
        if (lesson.getType() == LessonType.QUIZ) {
            throw new BusinessRuleException("A quiz lesson can't also have an assignment");
        }
        Assignment assignment = assignmentRepository.findByLessonId(lessonId)
                .orElseGet(() -> Assignment.builder().lesson(lesson).build());
        assignment.setInstructions(request.instructions().trim());
        assignment.setPassScore(request.passScore());
        assignment.setRequired(request.required());
        return toResponse(assignmentRepository.save(assignment), null);
    }

    @Override
    @Transactional
    public void delete(Actor actor, Integer lessonId) {
        Lesson lesson = findLesson(lessonId);
        courseAccess.requireManager(lesson.getSection().getCourse(), actor);
        assignmentRepository.findByLessonId(lessonId).ifPresent(assignmentRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionPageResponse findUngraded(Actor actor, int page, int size) {
        PageRequest request = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.ASC, "submittedAt", "id")
        );
        Page<AssignmentSubmission> submissions = actor.admin()
                ? submissionRepository.findByStatus(SubmissionStatus.SUBMITTED, request)
                : submissionRepository.findByStatusAndCourseOwner(SubmissionStatus.SUBMITTED, actor.userId(), request);
        return new SubmissionPageResponse(
                submissions.map(this::toReviewResponse).getContent(),
                submissions.getNumber(),
                submissions.getSize(),
                submissions.getTotalElements(),
                submissions.getTotalPages()
        );
    }

    @Override
    @Transactional
    public SubmissionReviewResponse grade(Actor actor, Integer submissionId, AssignmentGradeRequest request) {
        AssignmentSubmission submission = submissionRepository.findByIdForUpdate(submissionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Submission", submissionId));
        Lesson lesson = submission.getAssignment().getLesson();
        Course course = lesson.getSection().getCourse();
        courseAccess.requireManager(course, actor);

        submission.setStatus(SubmissionStatus.GRADED);
        submission.setScore(request.score());
        submission.setFeedback(StringUtils.hasText(request.feedback()) ? request.feedback().trim() : null);
        submission.setGradedBy(actor.userId());
        submission.setGradedAt(Instant.now());

        if (submission.passed()) {
            lessonProgressService.completeForStudent(submission.getStudent().getId(), lesson.getId());
        }
        notificationService.notifyStudent(
                submission.getStudent().getId(),
                NotificationType.ASSIGNMENT,
                submission.passed() ? "Assignment passed" : "Assignment graded",
                "%s: %d/100.".formatted(lesson.getTitle(), request.score()),
                "/learn/%s?lesson=%d".formatted(course.getSlug(), lesson.getId())
        );
        log.info("Graded submission id={} score={} by user={}", submissionId, request.score(), actor.userId());
        return toReviewResponse(submission);
    }

    private Lesson findLesson(Integer lessonId) {
        return lessonRepository.findDetailedById(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
    }

    private AssignmentResponse toResponse(Assignment assignment, AssignmentSubmission submission) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getLesson().getId(),
                assignment.getInstructions(),
                assignment.getPassScore(),
                assignment.isRequired(),
                submission == null ? null : toResponse(submission)
        );
    }

    private SubmissionResponse toResponse(AssignmentSubmission submission) {
        return new SubmissionResponse(
                submission.getId(),
                fileService.playableUrl(submission.getFileUrl()),
                submission.getFileName(),
                submission.getComment(),
                submission.getStatus().name(),
                submission.getScore(),
                submission.passed(),
                submission.getFeedback(),
                submission.getSubmittedAt(),
                submission.getGradedAt()
        );
    }

    private SubmissionReviewResponse toReviewResponse(AssignmentSubmission submission) {
        Lesson lesson = submission.getAssignment().getLesson();
        Course course = lesson.getSection().getCourse();
        return new SubmissionReviewResponse(
                submission.getStudent().getName(),
                lesson.getId(),
                lesson.getTitle(),
                course.getSlug(),
                course.getTitle(),
                submission.getAssignment().getPassScore(),
                toResponse(submission)
        );
    }
}
