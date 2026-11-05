package com.course_aura_hub.instructor.service;

import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.dto.CourseCreateRequest;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.dto.CourseUpdateRequest;
import com.course_aura_hub.course.mapper.CourseAssembler;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.course.service.CourseService;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.instructor.dto.InstructorCourseResponse;
import com.course_aura_hub.instructor.dto.InstructorStudentResponse;
import com.course_aura_hub.instructor.dto.InstructorSummaryResponse;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.revision.service.CourseRevisionService;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstructorServiceImpl implements InstructorService {

    private static final String REVIEW_LINK = "/admin/course-reviews";

    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final CourseAssembler courseAssembler;
    private final CourseAccess courseAccess;
    private final CourseRevisionService revisionService;
    private final LessonRepository lessonRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public InstructorSummaryResponse summary(Actor actor) {
        List<Course> courses = courseRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(actor.userId());
        List<Course> rated = courses.stream().filter(course -> course.getRating().signum() > 0).toList();
        BigDecimal rating = rated.isEmpty() ? BigDecimal.ZERO
                : rated.stream().map(Course::getRating).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rated.size()), 2, RoundingMode.HALF_UP);
        return new InstructorSummaryResponse(
                courses.size(),
                (int) courses.stream().filter(course -> course.getStatus() == CourseStatus.PUBLISHED).count(),
                (int) courses.stream().filter(course -> course.getStatus() == CourseStatus.PENDING_REVIEW).count(),
                courses.stream().mapToInt(Course::getTotalStudents).sum(),
                courses.stream().map(Course::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add),
                rating
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstructorCourseResponse> findMine(Actor actor) {
        return courseRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(actor.userId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorCourseResponse find(Actor actor, String slug) {
        return toResponse(requireManaged(actor, slug));
    }

    @Override
    @Transactional
    public InstructorCourseResponse create(Actor actor, CourseCreateRequest request) {
        CourseResponse created = courseService.create(new CourseCreateRequest(
                request.title(),
                request.description(),
                displayName(actor),
                request.price(),
                request.originalPrice(),
                null,
                request.category(),
                request.level(),
                request.thumbnail(),
                request.totalLessons(),
                request.totalHours(),
                request.tags()
        ));
        Course course = courseAccess.requireCourse(created.slug());
        course.setOwnerId(actor.userId());
        log.info("Instructor {} created course id={}", actor.userId(), course.getId());
        return toResponse(course);
    }

    @Override
    @Transactional
    public InstructorCourseResponse update(Actor actor, String slug, CourseUpdateRequest request) {
        Course course = requireManaged(actor, slug);
        if (course.getStatus() == CourseStatus.PENDING_REVIEW) {
            throw new BusinessRuleException("This course is being reviewed, so it can't be changed right now");
        }
        // A live instructor course changes only through an approved revision; admins edit directly
        if (course.getStatus() == CourseStatus.PUBLISHED && course.getOwnerId() != null && !actor.admin()) {
            revisionService.propose(actor, course, request);
            return toResponse(course);
        }
        courseService.update(slug, new CourseUpdateRequest(
                request.title(),
                request.description(),
                null,
                request.price(),
                request.originalPrice(),
                null,
                request.category(),
                request.level(),
                request.thumbnail(),
                request.totalLessons(),
                request.totalHours(),
                request.tags()
        ));
        return toResponse(course);
    }

    @Override
    @Transactional
    public void delete(Actor actor, String slug) {
        Course course = requireManaged(actor, slug);
        if (course.getStatus() != CourseStatus.DRAFT || course.getTotalStudents() > 0) {
            throw new BusinessRuleException("Only a draft with no students can be deleted");
        }
        courseService.delete(slug);
    }

    @Override
    @Transactional
    public InstructorCourseResponse submitForReview(Actor actor, String slug) {
        Course course = requireManaged(actor, slug);
        if (course.getStatus() != CourseStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft can be sent for review");
        }
        if (lessonRepository.countByCourseId(course.getId()) == 0) {
            throw new BusinessRuleException("Add at least one lesson before sending this course for review");
        }
        course.setStatus(CourseStatus.PENDING_REVIEW);
        course.setReviewNote(null);
        notificationService.notifyAdmins(
                NotificationType.COURSE_REVIEW,
                "Course waiting for review",
                "%s sent %s for review.".formatted(course.getInstructor(), course.getTitle()),
                REVIEW_LINK
        );
        return toResponse(course);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstructorStudentResponse> findStudents(Actor actor, String slug) {
        Course course = requireManaged(actor, slug);
        return studentCourseRepository.findWithStudentByCourseId(course.getId()).stream()
                .map(enrollment -> new InstructorStudentResponse(
                        enrollment.getStudent().getId(),
                        enrollment.getStudent().getName(),
                        enrollment.getStudent().getAvatar(),
                        enrollment.isCompleted()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> findAwaitingReview() {
        return courseRepository.findByStatusOrderByUpdatedAtAscIdAsc(CourseStatus.PENDING_REVIEW).stream()
                .map(course -> courseAssembler.toResponse(course, null))
                .toList();
    }

    @Override
    @Transactional
    public CourseResponse approve(String slug) {
        Course course = requireAwaitingReview(slug);
        course.setStatus(CourseStatus.PUBLISHED);
        course.setReviewNote(null);
        notifyOwner(course, "Course approved", "%s is now live.".formatted(course.getTitle()));
        log.info("Approved course id={}", course.getId());
        return courseAssembler.toResponse(course, null);
    }

    @Override
    @Transactional
    public CourseResponse reject(String slug, String note) {
        Course course = requireAwaitingReview(slug);
        course.setStatus(CourseStatus.DRAFT);
        course.setReviewNote(note.trim());
        notifyOwner(course, "Course needs changes", course.getReviewNote());
        log.info("Sent course id={} back to its instructor", course.getId());
        return courseAssembler.toResponse(course, null);
    }

    private Course requireManaged(Actor actor, String slug) {
        Course course = courseAccess.requireCourse(slug);
        courseAccess.requireManager(course, actor);
        return course;
    }

    private Course requireAwaitingReview(String slug) {
        Course course = courseAccess.requireCourse(slug);
        if (course.getStatus() != CourseStatus.PENDING_REVIEW) {
            throw new BusinessRuleException("This course isn't waiting for review");
        }
        return course;
    }

    private void notifyOwner(Course course, String title, String message) {
        if (course.getOwnerId() != null) {
            notificationService.notifyUsers(
                    List.of(course.getOwnerId()), NotificationType.COURSE_REVIEW, title, message,
                    "/instructor/courses/" + course.getSlug());
        }
    }

    private String displayName(Actor actor) {
        return userRepository.findById(actor.userId())
                .map(user -> ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                        + (user.getLastName() == null ? "" : user.getLastName())).trim())
                .filter(StringUtils::hasText)
                .orElseThrow(() -> ResourceNotFoundException.of("User", actor.userId()));
    }

    private InstructorCourseResponse toResponse(Course course) {
        return new InstructorCourseResponse(courseAssembler.toResponse(course, null), course.getReviewNote(),
                revisionService.findPending(course.getId()));
    }
}
