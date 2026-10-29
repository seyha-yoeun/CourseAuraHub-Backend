package com.course_aura_hub.dashboard.service;

import com.course_aura_hub.common.util.AnalyticsCalculator;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseMonthlyMetricRepository;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.course.repository.projection.CategoryRevenueProjection;
import com.course_aura_hub.course.repository.projection.MonthlyMetricProjection;
import com.course_aura_hub.dashboard.dto.DashboardStatsResponse;
import com.course_aura_hub.enrollment.model.Enrollment;
import com.course_aura_hub.enrollment.repository.EnrollmentRepository;
import com.course_aura_hub.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int MONTHS_IN_YEAR = 12;
    private static final int RECENT_ENROLLMENT_LIMIT = 10;
    private static final int TOP_COURSE_LIMIT = 5;

    private final CourseRepository courseRepository;
    private final CourseMonthlyMetricRepository monthlyMetricRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AnalyticsCalculator analyticsCalculator;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        List<MonthlyMetricProjection> monthlyMetrics = monthlyMetricRepository.aggregateByMonth();
        Map<Short, Long> enrollmentsByMonth = new LinkedHashMap<>();
        Map<Short, BigDecimal> revenueByMonth = new LinkedHashMap<>();

        for (MonthlyMetricProjection metric : monthlyMetrics) {
            enrollmentsByMonth.put(metric.getMonth(), metric.getEnrollments() == null ? 0L : metric.getEnrollments());
            revenueByMonth.put(metric.getMonth(), metric.getRevenue() == null ? BigDecimal.ZERO : metric.getRevenue());
        }

        int currentMonth = LocalDate.now().getMonthValue();
        int previousMonth = currentMonth == 1 ? MONTHS_IN_YEAR : currentMonth - 1;

        BigDecimal currentRevenue = revenueByMonth.getOrDefault((short) currentMonth, BigDecimal.ZERO);
        BigDecimal previousRevenue = revenueByMonth.getOrDefault((short) previousMonth, BigDecimal.ZERO);
        long currentEnrollments = enrollmentsByMonth.getOrDefault((short) currentMonth, 0L);
        long previousEnrollments = enrollmentsByMonth.getOrDefault((short) previousMonth, 0L);

        DashboardStatsResponse.Kpis kpis = new DashboardStatsResponse.Kpis(
                scaleMoney(courseRepository.sumRevenue()),
                Math.toIntExact(studentRepository.count()),
                Math.toIntExact(courseRepository.count()),
                Math.toIntExact(courseRepository.countByStatus(CourseStatus.PUBLISHED)),
                scaleRating(courseRepository.averageRating()),
                analyticsCalculator.percentageChange(currentRevenue, previousRevenue),
                analyticsCalculator.percentageChange(currentEnrollments, previousEnrollments)
        );

        DashboardStatsResponse.Charts charts = new DashboardStatsResponse.Charts(
                monthLabels(),
                monthRevenue(revenueByMonth),
                monthEnrollments(enrollmentsByMonth),
                revenueByCategory()
        );

        List<DashboardStatsResponse.TopCourse> topCourses = courseRepository
                .findTop5ByOrderByRevenueDesc()
                .stream()
                .limit(TOP_COURSE_LIMIT)
                .map(this::toTopCourse)
                .toList();

        List<DashboardStatsResponse.RecentEnrollment> recentEnrollments = enrollmentRepository
                .findAllByOrderByEnrolledAtDescIdDesc(PageRequest.of(0, RECENT_ENROLLMENT_LIMIT))
                .stream()
                .map(this::toRecentEnrollment)
                .toList();

        log.debug("Computed dashboard stats with {} monthly buckets", monthlyMetrics.size());
        return new DashboardStatsResponse(kpis, charts, topCourses, recentEnrollments);
    }

    private List<String> monthLabels() {
        List<String> labels = new ArrayList<>(MONTHS_IN_YEAR);
        for (int month = 1; month <= MONTHS_IN_YEAR; month++) {
            labels.add(java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
        }
        return labels;
    }

    private List<BigDecimal> monthRevenue(Map<Short, BigDecimal> values) {
        List<BigDecimal> result = new ArrayList<>(MONTHS_IN_YEAR);
        for (short month = 1; month <= MONTHS_IN_YEAR; month++) {
            result.add(scaleMoney(values.getOrDefault(month, BigDecimal.ZERO)));
        }
        return result;
    }

    private List<Integer> monthEnrollments(Map<Short, Long> values) {
        List<Integer> result = new ArrayList<>(MONTHS_IN_YEAR);
        for (short month = 1; month <= MONTHS_IN_YEAR; month++) {
            result.add(Math.toIntExact(values.getOrDefault(month, 0L)));
        }
        return result;
    }

    private Map<String, BigDecimal> revenueByCategory() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (CategoryRevenueProjection projection : courseRepository.sumRevenueByCategory()) {
            result.put(projection.getCategory(), scaleMoney(projection.getRevenue()));
        }
        return result;
    }

    private DashboardStatsResponse.TopCourse toTopCourse(Course course) {
        return new DashboardStatsResponse.TopCourse(
                course.getSlug(),
                course.getTitle(),
                scaleMoney(course.getRevenue()),
                course.getTotalStudents(),
                scaleRating(course.getRating()),
                course.getCategory(),
                course.getPrice(),
                course.getStatus().apiValue()
        );
    }

    private DashboardStatsResponse.RecentEnrollment toRecentEnrollment(Enrollment enrollment) {
        return new DashboardStatsResponse.RecentEnrollment(
                enrollment.getId(),
                enrollment.getStudent().getName(),
                enrollment.getStudent().getAvatar(),
                enrollment.getStudent().getCountry(),
                enrollment.getCourse().getTitle(),
                enrollment.getCourse().getSlug(),
                enrollment.getAmount(),
                enrollment.getEnrolledAt()
        );
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleRating(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(1, RoundingMode.HALF_UP);
    }
}
