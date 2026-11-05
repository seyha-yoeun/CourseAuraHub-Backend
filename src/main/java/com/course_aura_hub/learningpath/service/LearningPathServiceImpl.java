package com.course_aura_hub.learningpath.service;

import com.course_aura_hub.cart.service.CartService;
import com.course_aura_hub.certificate.model.CourseCertificate;
import com.course_aura_hub.certificate.service.CertificateNumberGenerator;
import com.course_aura_hub.certificate.service.CertificatePdfRenderer;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.mapper.CourseAssembler;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.facade.LearnerFacade;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.learningpath.dto.LearningPathEnrollResponse;
import com.course_aura_hub.learningpath.dto.LearningPathProgressResponse;
import com.course_aura_hub.learningpath.dto.LearningPathRequest;
import com.course_aura_hub.learningpath.dto.LearningPathResponse;
import com.course_aura_hub.learningpath.model.LearningPath;
import com.course_aura_hub.learningpath.model.LearningPathCertificate;
import com.course_aura_hub.learningpath.model.LearningPathCourse;
import com.course_aura_hub.learningpath.model.LearningPathStatus;
import com.course_aura_hub.learningpath.repository.LearningPathCertificateRepository;
import com.course_aura_hub.learningpath.repository.LearningPathRepository;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentCourse;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathServiceImpl implements LearningPathService {

    private static final String PATH_ISSUER = "CourseAuraHub learning path";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final LearningPathRepository pathRepository;
    private final LearningPathCertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final CourseAssembler courseAssembler;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;
    private final LearnerFacade learnerFacade;
    private final CartService cartService;
    private final NotificationService notificationService;
    private final CertificateNumberGenerator numberGenerator;
    private final CertificatePdfRenderer pdfRenderer;

    @Override
    @Transactional(readOnly = true)
    public List<LearningPathResponse> findPublished() {
        return pathRepository.findByStatusOrderByTitleAsc(LearningPathStatus.PUBLISHED).stream()
                .map(path -> toResponse(path, true))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LearningPathResponse findPublished(String slug) {
        return toResponse(requirePublished(slug), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningPathResponse> findAllForAdmin() {
        return pathRepository.findAllByOrderByUpdatedAtDescIdDesc().stream()
                .map(path -> toResponse(path, false))
                .toList();
    }

    @Override
    @Transactional
    public LearningPathResponse create(LearningPathRequest request) {
        if (!StringUtils.hasText(request.title())) {
            throw new BusinessRuleException("Title is required");
        }
        LearningPath path = LearningPath.builder()
                .slug(uniqueSlug(request.title()))
                .title(request.title().trim())
                .description(request.description() == null ? "" : request.description().trim())
                .thumbnail(blankToNull(request.thumbnail()))
                .status(LearningPathStatus.fromValue(request.status()))
                .build();
        LearningPath saved = pathRepository.save(path);
        if (request.courseSlugs() != null) {
            replaceCourses(saved, request.courseSlugs());
        }
        requirePublishable(saved);
        log.info("Created learning path id={} slug={} courses={}", saved.getId(), saved.getSlug(), saved.getCourses().size());
        return toResponse(saved, false);
    }

    @Override
    @Transactional
    public LearningPathResponse update(String slug, LearningPathRequest request) {
        LearningPath path = requirePath(slug);
        if (request.title() != null) {
            if (!StringUtils.hasText(request.title())) {
                throw new BusinessRuleException("Title cannot be blank");
            }
            path.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            path.setDescription(request.description().trim());
        }
        if (request.thumbnail() != null) {
            path.setThumbnail(blankToNull(request.thumbnail()));
        }
        if (request.status() != null) {
            path.setStatus(LearningPathStatus.fromValue(request.status()));
        }
        if (request.courseSlugs() != null) {
            replaceCourses(path, request.courseSlugs());
        }
        requirePublishable(path);
        log.info("Updated learning path id={} slug={}", path.getId(), slug);
        return toResponse(path, false);
    }

    @Override
    @Transactional
    public void delete(String slug) {
        LearningPath path = requirePath(slug);
        pathRepository.delete(path);
        log.info("Deleted learning path id={} slug={}", path.getId(), slug);
    }

    @Override
    @Transactional
    public List<LearningPathProgressResponse> findMine(String email) {
        Optional<Student> student = learnerAccountService.findCurrentStudent(email);
        if (student.isEmpty()) {
            return List.of();
        }
        Map<Integer, Boolean> enrollments = enrollments(student.get());
        if (enrollments.isEmpty()) {
            return List.of();
        }
        return pathRepository.findByStatusContainingCourses(LearningPathStatus.PUBLISHED, enrollments.keySet()).stream()
                .map(path -> progress(path, student.get(), enrollments))
                .toList();
    }

    @Override
    @Transactional
    public LearningPathProgressResponse progress(String email, String slug) {
        LearningPath path = requirePublished(slug);
        Student student = learnerAccountService.currentStudent(email);
        return progress(path, student, enrollments(student));
    }

    @Override
    @Transactional
    public LearningPathEnrollResponse enroll(String email, String slug) {
        LearningPath path = requirePublished(slug);
        Student student = learnerAccountService.currentStudent(email);
        Map<Integer, Boolean> enrollments = enrollments(student);

        List<String> enrolled = new ArrayList<>();
        List<String> addedToCart = new ArrayList<>();
        List<String> alreadyEnrolled = new ArrayList<>();
        for (Course course : publishedCourses(path)) {
            if (enrollments.containsKey(course.getId())) {
                alreadyEnrolled.add(course.getSlug());
            } else if (course.currentPrice().price().signum() == 0) {
                learnerFacade.enroll(email, course.getSlug());
                enrolled.add(course.getSlug());
            } else {
                // Paid courses go through the normal checkout
                cartService.add(email, course.getSlug());
                addedToCart.add(course.getSlug());
            }
        }
        log.info("Learner enrolled in learning path pathId={} studentId={} enrolled={} carted={}",
                path.getId(), student.getId(), enrolled.size(), addedToCart.size());
        return new LearningPathEnrollResponse(enrolled, addedToCart, alreadyEnrolled);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] certificatePdf(String email, String certificateNumber, String language) {
        return learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> Optional.ofNullable(CertificateNumberGenerator.normalize(certificateNumber))
                        .flatMap(certificateRepository::findByCertificateNumber)
                        .filter(certificate -> certificate.getStudentId().equals(student.getId())))
                .map(certificate -> pdfRenderer.render(asPrintable(certificate), language))
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
    }

    private LearningPathProgressResponse progress(LearningPath path, Student student, Map<Integer, Boolean> enrollments) {
        List<Course> courses = publishedCourses(path);
        List<LearningPathProgressResponse.Step> steps = courses.stream()
                .map(course -> new LearningPathProgressResponse.Step(
                        course.getSlug(),
                        course.getTitle(),
                        course.getThumbnail(),
                        enrollments.containsKey(course.getId()),
                        Boolean.TRUE.equals(enrollments.get(course.getId()))))
                .toList();
        int completedCount = (int) steps.stream().filter(LearningPathProgressResponse.Step::completed).count();
        int enrolledCount = (int) steps.stream().filter(LearningPathProgressResponse.Step::enrolled).count();
        boolean complete = !steps.isEmpty() && completedCount == steps.size();

        Optional<LearningPathCertificate> certificate = certificateRepository.findByPathIdAndStudentId(path.getId(), student.getId());
        if (complete && certificate.isEmpty()) {
            certificate = Optional.of(issue(path, student));
        }
        return new LearningPathProgressResponse(
                path.getSlug(),
                path.getTitle(),
                steps.size(),
                enrolledCount,
                completedCount,
                steps.isEmpty() ? BigDecimal.ZERO.setScale(1)
                        : BigDecimal.valueOf(completedCount).multiply(ONE_HUNDRED)
                                .divide(BigDecimal.valueOf(steps.size()), 1, RoundingMode.HALF_UP),
                complete,
                steps.stream().filter(step -> !step.completed()).map(LearningPathProgressResponse.Step::slug)
                        .findFirst().orElse(null),
                certificate.map(found -> new LearningPathProgressResponse.Certificate(
                        found.getCertificateNumber(), found.getIssuedAt())).orElse(null),
                steps
        );
    }

    /** Path certificates are permanent: once issued they stay even if courses are added to the path later. */
    private LearningPathCertificate issue(LearningPath path, Student student) {
        LearningPathCertificate saved = certificateRepository.save(LearningPathCertificate.builder()
                .certificateNumber(numberGenerator.next())
                .pathId(path.getId())
                .studentId(student.getId())
                .learnerName(student.getName())
                .pathTitle(path.getTitle())
                .build());
        notificationService.notifyStudent(
                student.getId(),
                NotificationType.CERTIFICATE_ISSUED,
                "Learning path completed",
                "You completed every course in " + path.getTitle() + ". Your path certificate is ready.",
                "/certificates/" + saved.getCertificateNumber()
        );
        log.info("Issued learning path certificate pathId={} studentId={}", path.getId(), student.getId());
        return saved;
    }

    /** The course certificate renderer prints any title; nothing here is saved. */
    private static CourseCertificate asPrintable(LearningPathCertificate certificate) {
        return CourseCertificate.builder()
                .certificateNumber(certificate.getCertificateNumber())
                .learnerName(certificate.getLearnerName())
                .courseTitle(certificate.getPathTitle())
                .instructor(PATH_ISSUER)
                .issuedAt(certificate.getIssuedAt())
                .build();
    }

    private void replaceCourses(LearningPath path, List<String> slugs) {
        List<String> distinct = new ArrayList<>(new LinkedHashSet<>(slugs.stream().map(String::trim).toList()));
        Map<String, Course> bySlug = distinct.stream()
                .map(slug -> courseRepository.findBySlug(slug).orElseThrow(() -> ResourceNotFoundException.of("Course", slug)))
                .collect(Collectors.toMap(Course::getSlug, Function.identity()));
        path.getCourses().clear();
        // Removed rows must be gone before the same courses are added back with new positions
        pathRepository.flush();
        for (int i = 0; i < distinct.size(); i++) {
            Course course = bySlug.get(distinct.get(i));
            path.getCourses().add(LearningPathCourse.builder()
                    .id(new LearningPathCourse.Id(path.getId(), course.getId()))
                    .path(path)
                    .course(course)
                    .position(i)
                    .build());
        }
    }

    private static void requirePublishable(LearningPath path) {
        if (path.getStatus() == LearningPathStatus.PUBLISHED && path.getCourses().stream()
                .noneMatch(entry -> entry.getCourse().getStatus() == CourseStatus.PUBLISHED)) {
            throw new BusinessRuleException("A published path needs at least one published course");
        }
    }

    private Map<Integer, Boolean> enrollments(Student student) {
        return studentCourseRepository.findByStudentIdWithCourse(student.getId()).stream()
                .collect(Collectors.toMap(sc -> sc.getCourse().getId(), StudentCourse::isCompleted, (a, b) -> a || b));
    }

    /** Learners only ever see a path's published courses; an unpublished one is skipped, not a blocker. */
    private static List<Course> publishedCourses(LearningPath path) {
        return path.getCourses().stream()
                .map(LearningPathCourse::getCourse)
                .filter(course -> course.getStatus() == CourseStatus.PUBLISHED)
                .toList();
    }

    private LearningPathResponse toResponse(LearningPath path, boolean publicView) {
        List<Course> courses = publicView ? publishedCourses(path)
                : path.getCourses().stream().map(LearningPathCourse::getCourse).toList();
        List<CourseResponse> courseResponses = courses.stream()
                .map(course -> publicView ? courseAssembler.toPublicResponse(course, null) : courseAssembler.toResponse(course, null))
                .toList();
        return new LearningPathResponse(
                path.getId(),
                path.getSlug(),
                path.getTitle(),
                path.getDescription(),
                path.getThumbnail(),
                path.getStatus().apiValue(),
                courses.size(),
                courses.stream().mapToInt(course -> course.getTotalLessons() == null ? 0 : course.getTotalLessons()).sum(),
                courses.stream().map(Course::getTotalHours).filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                courses.stream().map(course -> course.currentPrice().price()).reduce(BigDecimal.ZERO, BigDecimal::add),
                courseResponses,
                path.getUpdatedAt()
        );
    }

    private LearningPath requirePath(String slug) {
        return pathRepository.findBySlug(slug).orElseThrow(() -> ResourceNotFoundException.of("Learning path", slug));
    }

    private LearningPath requirePublished(String slug) {
        return pathRepository.findBySlug(slug)
                .filter(path -> path.getStatus() == LearningPathStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Learning path", slug));
    }

    private String uniqueSlug(String title) {
        String base = title.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(base)) {
            base = "path";
        }
        String candidate = base;
        int suffix = 1;
        while (pathRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
