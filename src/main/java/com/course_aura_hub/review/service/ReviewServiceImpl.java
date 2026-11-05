package com.course_aura_hub.review.service;

import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.review.dto.ReviewPageResponse;
import com.course_aura_hub.review.dto.ReviewRequest;
import com.course_aura_hub.review.dto.ReviewResponse;
import com.course_aura_hub.review.mapper.ReviewAssembler;
import com.course_aura_hub.review.model.CourseReview;
import com.course_aura_hub.review.model.ReviewVote;
import com.course_aura_hub.review.repository.CourseReviewRepository;
import com.course_aura_hub.review.repository.ReviewVoteRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CourseReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;
    private final ReviewAssembler reviewAssembler;
    private final ReviewVoteRepository voteRepository;
    private final CourseAccess courseAccess;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public ReviewPageResponse findByCourse(String courseSlug, int page, int size, boolean includeUnpublished) {
        return findByCourse(courseSlug, page, size, includeUnpublished, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPageResponse findByCourse(String courseSlug, int page, int size, boolean includeUnpublished, String sort) {
        Course course = findCourse(courseSlug);
        if (!includeUnpublished && course.getStatus() != CourseStatus.PUBLISHED) {
            throw ResourceNotFoundException.of("Course", courseSlug);
        }

        PageRequest pageRequest = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                "helpful".equalsIgnoreCase(sort)
                        ? Sort.by(Sort.Direction.DESC, "helpfulCount", "createdAt", "id")
                        : Sort.by(Sort.Direction.DESC, "createdAt", "id")
        );
        // Admins see hidden reviews too, so they can moderate them
        Page<CourseReview> reviews = includeUnpublished
                ? reviewRepository.findByCourseId(course.getId(), pageRequest)
                : reviewRepository.findByCourseIdAndHiddenFalse(course.getId(), pageRequest);

        return new ReviewPageResponse(
                reviews.map(reviewAssembler::toResponse).getContent(),
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages(),
                course.getRating()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewResponse> findMine(String email, String courseSlug) {
        Course course = findCourse(courseSlug);
        return learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> reviewRepository.findByCourseIdAndStudentId(course.getId(), student.getId()))
                .map(reviewAssembler::toResponse);
    }

    @Override
    @Transactional
    public ReviewResponse upsertMine(String email, String courseSlug, ReviewRequest request) {
        Course course = findCourse(courseSlug);
        Student student = learnerAccountService.findCurrentStudent(email)
                .filter(s -> studentCourseRepository.existsByStudentIdAndCourseId(s.getId(), course.getId()))
                .orElseThrow(() -> new ForbiddenActionException("You must be enrolled in this course to review it"));

        CourseReview review = reviewRepository.findByCourseIdAndStudentId(course.getId(), student.getId())
                .orElseGet(() -> CourseReview.builder().course(course).student(student).build());
        boolean created = review.getId() == null;

        review.setRating(request.rating().shortValue());
        review.setComment(StringUtils.hasText(request.comment()) ? request.comment().trim() : null);
        CourseReview saved = reviewRepository.saveAndFlush(review);

        refreshCourseRating(course.getId());
        log.info("{} review id={} courseId={} studentId={} rating={}",
                created ? "Created" : "Updated", saved.getId(), course.getId(), student.getId(), saved.getRating());
        return reviewAssembler.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteMine(String email, String courseSlug) {
        Course course = findCourse(courseSlug);
        CourseReview review = learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> reviewRepository.findByCourseIdAndStudentId(course.getId(), student.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Review for course", courseSlug));

        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshCourseRating(course.getId());
        log.info("Deleted own review id={} courseId={}", review.getId(), course.getId());
    }

    @Override
    @Transactional
    public void deleteAsAdmin(String courseSlug, Integer reviewId) {
        Course course = findCourse(courseSlug);
        CourseReview review = reviewRepository.findByIdAndCourseId(reviewId, course.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId));

        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshCourseRating(course.getId());
        log.info("Admin deleted review id={} courseId={}", reviewId, course.getId());
    }

    @Override
    @Transactional
    public ReviewResponse reply(Actor actor, String courseSlug, Integer reviewId, String body) {
        Course course = findCourse(courseSlug);
        courseAccess.requireManager(course, actor);
        if (!StringUtils.hasText(body) || body.trim().length() > 2000) {
            throw new BusinessRuleException("A reply needs 1 to 2000 characters");
        }
        CourseReview review = reviewRepository.findByIdAndCourseId(reviewId, course.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId));
        review.setReply(body.trim());
        review.setReplyAuthorId(actor.userId());
        review.setReplyAuthorName(userRepository.findById(actor.userId())
                .map(user -> ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                        + (user.getLastName() == null ? "" : user.getLastName())).trim())
                .filter(StringUtils::hasText).orElse("Course team"));
        review.setRepliedAt(Instant.now());
        notificationService.notifyStudent(review.getStudent().getId(), NotificationType.COURSE_REVIEW,
                "Reply to your review", review.getReplyAuthorName() + " replied to your review of " + course.getTitle() + ".",
                "/courses/" + course.getSlug());
        log.info("Replied to review id={} courseId={}", reviewId, course.getId());
        return reviewAssembler.toResponse(review);
    }

    @Override
    @Transactional
    public void deleteReply(Actor actor, String courseSlug, Integer reviewId) {
        Course course = findCourse(courseSlug);
        courseAccess.requireManager(course, actor);
        CourseReview review = reviewRepository.findByIdAndCourseId(reviewId, course.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId));
        review.setReply(null);
        review.setReplyAuthorId(null);
        review.setReplyAuthorName(null);
        review.setRepliedAt(null);
    }

    @Override
    @Transactional
    public ReviewResponse setHelpful(String email, Integer reviewId, boolean helpful) {
        CourseReview review = reviewRepository.findById(reviewId)
                .filter(found -> !found.isHidden())
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId));
        Student student = learnerAccountService.currentStudent(email);
        if (review.getStudent().getId().equals(student.getId())) {
            throw new BusinessRuleException("You can't vote on your own review");
        }
        ReviewVote.Id id = new ReviewVote.Id(reviewId, student.getId());
        if (helpful && !voteRepository.existsById(id)) {
            voteRepository.saveAndFlush(ReviewVote.builder().id(id).build());
        } else if (!helpful && voteRepository.existsById(id)) {
            voteRepository.deleteById(id);
            voteRepository.flush();
        }
        // Counted from the votes, so parallel votes can't drift
        review.setHelpfulCount((int) voteRepository.countByIdReviewId(reviewId));
        return reviewAssembler.toResponse(review);
    }

    @Override
    @Transactional
    public void refreshRating(Integer courseId) {
        refreshCourseRating(courseId);
    }

    /**
     * Recomputes {@code courses.rating} from real reviews. When a course has no reviews the existing
     * (seeded) rating is kept.
     */
    private void refreshCourseRating(Integer courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseId));
        Double average = reviewRepository.averageRating(courseId);
        if (average == null) {
            return;
        }
        course.setRating(BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP));
        log.debug("Refreshed rating courseId={} rating={}", courseId, course.getRating());
    }

    private Course findCourse(String slug) {
        return courseRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", slug));
    }
}
