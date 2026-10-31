package com.course_aura_hub.enrollment.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseMonthlyMetric;
import com.course_aura_hub.course.repository.CourseMonthlyMetricRepository;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.enrollment.dto.EnrollmentCreateRequest;
import com.course_aura_hub.enrollment.dto.EnrollmentResponse;
import com.course_aura_hub.enrollment.mapper.EnrollmentAssembler;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import com.course_aura_hub.enrollment.model.Enrollment;
import com.course_aura_hub.enrollment.repository.EnrollmentRepository;
import com.course_aura_hub.settings.service.SettingsService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentCourse;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import com.course_aura_hub.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final int MAX_RECENT_ENROLLMENTS = 100;

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final CourseMonthlyMetricRepository monthlyMetricRepository;
    private final EnrollmentAssembler enrollmentAssembler;
    private final NotificationService notificationService;
    private final SettingsService settingsService;

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECENT_ENROLLMENTS));
        return enrollmentRepository.findAllByOrderByEnrolledAtDescIdDesc(PageRequest.of(0, safeLimit))
                .stream()
                .map(enrollmentAssembler::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public EnrollmentResponse create(EnrollmentCreateRequest request) {
        if (enrollmentRepository.existsByStudentIdAndCourseId(request.studentId(), request.courseId())) {
            throw new DuplicateResourceException("Student is already enrolled in this course");
        }

        Student student = studentRepository.findByIdForUpdate(request.studentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Student", request.studentId()));
        Course course = courseRepository.findByIdForUpdate(request.courseId())
                .orElseThrow(() -> ResourceNotFoundException.of("Course", request.courseId()));

        if (studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            throw new DuplicateResourceException("Student is already enrolled in this course");
        }

        LocalDate enrolledAt = request.enrolledAt() == null ? LocalDate.now() : request.enrolledAt();
        BigDecimal amount = request.amount() == null ? course.currentPrice().price() : request.amount();
        if (amount.signum() < 0) {
            throw new BusinessRuleException("Enrollment amount cannot be negative");
        }

        StudentCourse studentCourse = StudentCourse.builder()
                .student(student)
                .course(course)
                .completed(false)
                .build();
        studentCourseRepository.save(studentCourse);

        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .course(course)
                .enrolledAt(enrolledAt)
                .amount(amount)
                .build();
        Enrollment saved = enrollmentRepository.save(enrollment);

        course.setTotalStudents(course.getTotalStudents() + 1);
        course.setRevenue(course.getRevenue().add(amount));
        course.setUpdatedAt(LocalDate.now());

        student.setTotalSpent(student.getTotalSpent().add(amount));
        student.setLastActive(LocalDate.now());

        incrementMonthlyMetric(course, enrolledAt, amount);

        notificationService.notifyStudent(
                student.getId(),
                NotificationType.ENROLLED,
                "You're enrolled",
                "You now have access to " + course.getTitle() + ".",
                "/learn/" + course.getSlug()
        );
        if (settingsService.isNewEnrollmentAlertEnabled()) {
            notificationService.notifyAdmins(
                    NotificationType.NEW_ENROLLMENT,
                    "New enrollment",
                    student.getName() + " enrolled in " + course.getTitle() + ".",
                    "/courses/" + course.getSlug()
            );
        }

        log.info(
                "Created enrollment id={} studentId={} courseId={} amount={}",
                saved.getId(),
                student.getId(),
                course.getId(),
                amount
        );
        return enrollmentAssembler.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Enrollment enrollment = enrollmentRepository.findDetailedById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Enrollment", id));

        Student student = studentRepository.findByIdForUpdate(enrollment.getStudent().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Student", enrollment.getStudent().getId()));
        Course course = courseRepository.findByIdForUpdate(enrollment.getCourse().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Course", enrollment.getCourse().getId()));

        BigDecimal amount = enrollment.getAmount();
        LocalDate enrolledAt = enrollment.getEnrolledAt();

        enrollmentRepository.delete(enrollment);
        enrollmentRepository.flush();

        studentCourseRepository.findByStudentIdAndCourseId(student.getId(), course.getId())
                .ifPresent(studentCourseRepository::delete);

        course.setTotalStudents(Math.max(0, course.getTotalStudents() - 1));
        course.setRevenue(course.getRevenue().subtract(amount).max(BigDecimal.ZERO));
        course.setUpdatedAt(LocalDate.now());
        student.setTotalSpent(student.getTotalSpent().subtract(amount).max(BigDecimal.ZERO));

        decrementMonthlyMetric(course, enrolledAt, amount);

        log.info("Deleted enrollment id={} studentId={} courseId={}", id, student.getId(), course.getId());
    }

    private void incrementMonthlyMetric(Course course, LocalDate enrolledAt, BigDecimal amount) {
        short month = (short) enrolledAt.getMonthValue();
        CourseMonthlyMetric metric = monthlyMetricRepository
                .findByCourseIdAndMonth(course.getId(), month)
                .orElseGet(() -> CourseMonthlyMetric.builder()
                        .course(course)
                        .month(month)
                        .enrollments(0)
                        .revenue(BigDecimal.ZERO)
                        .build());

        metric.setEnrollments(metric.getEnrollments() + 1);
        metric.setRevenue(metric.getRevenue().add(amount));
        monthlyMetricRepository.save(metric);
    }

    private void decrementMonthlyMetric(Course course, LocalDate enrolledAt, BigDecimal amount) {
        short month = (short) enrolledAt.getMonthValue();
        monthlyMetricRepository.findByCourseIdAndMonth(course.getId(), month).ifPresent(metric -> {
            metric.setEnrollments(Math.max(0, metric.getEnrollments() - 1));
            metric.setRevenue(metric.getRevenue().subtract(amount).max(BigDecimal.ZERO));
            monthlyMetricRepository.save(metric);
        });
    }
}
