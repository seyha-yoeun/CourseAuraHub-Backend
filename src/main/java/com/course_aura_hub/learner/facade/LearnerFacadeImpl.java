package com.course_aura_hub.learner.facade;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.curriculum.dto.CourseProgress;
import com.course_aura_hub.curriculum.dto.NextLessonResponse;
import com.course_aura_hub.curriculum.repository.CourseProgressProjection;
import com.course_aura_hub.curriculum.service.LessonProgressService;
import com.course_aura_hub.enrollment.dto.EnrollmentCreateRequest;
import com.course_aura_hub.enrollment.dto.EnrollmentResponse;
import com.course_aura_hub.enrollment.model.Enrollment;
import com.course_aura_hub.enrollment.repository.EnrollmentRepository;
import com.course_aura_hub.enrollment.service.EnrollmentService;
import com.course_aura_hub.learner.dto.AchievementResponse;
import com.course_aura_hub.learner.dto.DailyActivityResponse;
import com.course_aura_hub.learner.dto.LearnerDashboardResponse;
import com.course_aura_hub.learner.dto.MyCourseResponse;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.learner.service.LearnerStats;
import com.course_aura_hub.learner.service.LearnerStatsService;
import com.course_aura_hub.learner.service.WishlistService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentCourse;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearnerFacadeImpl implements LearnerFacade {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private final LearnerAccountService learnerAccountService;
    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final CourseRepository courseRepository;
    private final LessonProgressService lessonProgressService;
    private final WishlistService wishlistService;
    private final LearnerStatsService learnerStatsService;

    @Override
    @Transactional(readOnly = true)
    public List<MyCourseResponse> findMyCourses(String email) {
        return learnerAccountService.findCurrentStudent(email)
                .map(this::buildMyCourses)
                .orElse(List.of());
    }

    @Override
    @Transactional
    public MyCourseResponse enroll(String email, String courseSlug) {
        Course course = courseRepository.findBySlug(courseSlug)
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));

        if (course.currentPrice().price().signum() > 0) {
            throw new BusinessRuleException("This course requires checkout. Paid enrollment is not available yet");
        }

        Student student = learnerAccountService.currentStudent(email);
        EnrollmentResponse enrollment = enrollmentService.create(
                new EnrollmentCreateRequest(student.getId(), course.getId(), BigDecimal.ZERO, null)
        );
        wishlistService.removeIfPresent(student.getId(), course.getId());
        log.info("Learner self-enrolled studentId={} courseId={} enrollmentId={}",
                student.getId(), course.getId(), enrollment.id());

        return new MyCourseResponse(
                course.getId(),
                course.getSlug(),
                course.getTitle(),
                course.getThumbnail(),
                course.getInstructor(),
                course.getLevel().apiValue(),
                LocalDate.now(),
                CourseProgress.of(0, course.getTotalLessons(), false),
                null,
                lessonProgressService.findNextLessons(student.getId(), List.of(course.getId())).get(course.getId())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LearnerDashboardResponse dashboard(String email, String timeZone) {
        ZoneId zone = resolveZone(timeZone);
        LocalDate today = LocalDate.now(zone);
        Optional<Student> student = learnerAccountService.findCurrentStudent(email);
        List<MyCourseResponse> myCourses = student.map(this::buildMyCourses).orElse(List.of());
        LearnerStats stats = stats(student, zone, today);

        int completedCourses = (int) myCourses.stream().filter(course -> course.progress().completed()).count();
        List<DailyActivityResponse> lastSevenDays = IntStream.iterate(6, daysAgo -> daysAgo >= 0, daysAgo -> daysAgo - 1)
                .mapToObj(today::minusDays)
                .map(day -> new DailyActivityResponse(day, stats.lessonsByDay().getOrDefault(day, 0L)))
                .toList();
        // My courses are ordered by recent activity, so the first unfinished one is the natural place to resume
        MyCourseResponse continueLearning = myCourses.stream()
                .filter(course -> !course.progress().completed())
                .findFirst()
                .orElse(null);

        return new LearnerDashboardResponse(
                myCourses.size(),
                myCourses.size() - completedCourses,
                completedCourses,
                stats.certificatesEarned(),
                stats.lessonsCompleted(),
                stats.minutesLearned(),
                stats.streak(),
                lastSevenDays,
                continueLearning,
                zone.getId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementResponse> achievements(String email, String timeZone) {
        ZoneId zone = resolveZone(timeZone);
        LearnerStats stats = stats(learnerAccountService.findCurrentStudent(email), zone, LocalDate.now(zone));
        return learnerStatsService.achievements(stats);
    }

    private LearnerStats stats(Optional<Student> student, ZoneId zone, LocalDate today) {
        return student
                .map(found -> learnerStatsService.compute(found.getId(), zone, today))
                .orElse(LearnerStats.EMPTY);
    }

    private static ZoneId resolveZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return UTC;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException ex) {
            throw new BusinessRuleException("Unknown time zone: " + timeZone);
        }
    }

    private List<MyCourseResponse> buildMyCourses(Student student) {
        List<StudentCourse> studentCourses = studentCourseRepository.findByStudentIdWithCourse(student.getId());
        Map<Integer, LocalDate> enrolledAtByCourse = enrollmentRepository.findByStudentId(student.getId()).stream()
                .collect(Collectors.toMap(
                        enrollment -> enrollment.getCourse().getId(),
                        Enrollment::getEnrolledAt,
                        (first, second) -> first.isBefore(second) ? first : second
                ));
        Map<Integer, CourseProgressProjection> progressByCourse =
                lessonProgressService.summarizeByStudent(student.getId());
        Map<Integer, NextLessonResponse> nextLessonByCourse = lessonProgressService.findNextLessons(
                student.getId(),
                studentCourses.stream().map(studentCourse -> studentCourse.getCourse().getId()).toList()
        );

        return studentCourses.stream()
                .map(studentCourse -> {
                    Course course = studentCourse.getCourse();
                    CourseProgressProjection summary = progressByCourse.get(course.getId());
                    long completedLessons = summary == null ? 0 : summary.getCompletedLessons();
                    return new MyCourseResponse(
                            course.getId(),
                            course.getSlug(),
                            course.getTitle(),
                            course.getThumbnail(),
                            course.getInstructor(),
                            course.getLevel().apiValue(),
                            enrolledAtByCourse.get(course.getId()),
                            CourseProgress.of(completedLessons, course.getTotalLessons(), studentCourse.isCompleted()),
                            summary == null ? null : summary.getLastCompletedAt(),
                            studentCourse.isCompleted() ? null : nextLessonByCourse.get(course.getId())
                    );
                })
                // Most recently studied first, then most recently enrolled
                .sorted(Comparator
                        .comparing(MyCourseResponse::lastActivityAt, Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
                        .thenComparing(MyCourseResponse::enrolledAt, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())))
                .toList();
    }
}
