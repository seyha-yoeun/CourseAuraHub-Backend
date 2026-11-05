package com.course_aura_hub.instructor.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseMonthlyMetric;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse.Assignments;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse.Learners;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse.LessonFunnel;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse.Month;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse.Quiz;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse.Reviews;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse.Video;
import com.course_aura_hub.instructor.repository.InstructorAnalyticsRepository;
import com.course_aura_hub.instructor.repository.InstructorAnalyticsRepository.LearnerTotals;
import com.course_aura_hub.instructor.repository.InstructorAnalyticsRepository.LessonStats;
import com.course_aura_hub.instructor.repository.InstructorAnalyticsRepository.RatingCount;
import com.course_aura_hub.instructor.repository.InstructorAnalyticsRepository.SubmissionStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorAnalyticsServiceImpl implements InstructorAnalyticsService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    private final CourseAccess courseAccess;
    private final InstructorAnalyticsRepository analyticsRepository;

    @Override
    public CourseAnalyticsResponse courseAnalytics(Actor actor, String slug) {
        Course course = courseAccess.requireCourse(slug);
        courseAccess.requireManager(course, actor);
        Integer courseId = course.getId();

        LearnerTotals totals = analyticsRepository.findLearnerTotals(courseId);
        long enrolled = totals.getEnrolled();

        return new CourseAnalyticsResponse(
                course.getSlug(),
                course.getTitle(),
                new Learners(enrolled, totals.getCompleted(), percent(totals.getCompleted(), enrolled)),
                reviews(analyticsRepository.findRatingCounts(courseId)),
                assignments(analyticsRepository.findSubmissionStats(courseId)),
                monthly(course),
                funnel(analyticsRepository.findLessonStats(courseId), enrolled)
        );
    }

    private List<LessonFunnel> funnel(List<LessonStats> rows, long enrolled) {
        List<LessonFunnel> lessons = new ArrayList<>(rows.size());
        BigDecimal previousRate = null;
        for (LessonStats row : rows) {
            BigDecimal rate = percent(row.getCompletedLearners(), enrolled);
            BigDecimal dropOff = previousRate == null ? BigDecimal.ZERO.setScale(1)
                    : previousRate.subtract(rate).max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
            String type = row.getType().toLowerCase(Locale.ROOT);
            lessons.add(new LessonFunnel(
                    row.getLessonId(),
                    row.getSectionTitle(),
                    row.getTitle(),
                    type,
                    row.getCompletedLearners(),
                    rate,
                    dropOff,
                    "video".equals(type)
                            ? new Video(row.getWatchingLearners(), scale(row.getAverageWatchSeconds(), 0))
                            : null,
                    row.getQuizAttempts() > 0 || "quiz".equals(type) ? quiz(row) : null
            ));
            previousRate = rate;
        }
        return lessons;
    }

    private Quiz quiz(LessonStats row) {
        long learners = row.getQuizLearners();
        BigDecimal attemptsPerLearner = learners == 0 ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(row.getQuizAttempts()).divide(BigDecimal.valueOf(learners), 1, RoundingMode.HALF_UP);
        return new Quiz(
                row.getQuizAttempts(),
                learners,
                percent(row.getQuizPassedLearners(), learners),
                scale(row.getQuizAverageScore(), 1),
                attemptsPerLearner
        );
    }

    private Reviews reviews(List<RatingCount> counts) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int stars = MAX_RATING; stars >= MIN_RATING; stars--) {
            distribution.put(stars, 0L);
        }
        long total = 0;
        long points = 0;
        for (RatingCount count : counts) {
            distribution.put(count.getRating(), count.getReviews());
            total += count.getReviews();
            points += (long) count.getRating() * count.getReviews();
        }
        BigDecimal average = total == 0 ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(points).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new Reviews(total, average, distribution);
    }

    private Assignments assignments(SubmissionStats stats) {
        return new Assignments(stats.getPending(), stats.getGraded(), scale(stats.getAverageScore(), 1));
    }

    private List<Month> monthly(Course course) {
        return course.getMonthlyMetrics().stream()
                .sorted(Comparator.comparing(CourseMonthlyMetric::getMonth))
                .map(metric -> new Month(metric.getMonth(), metric.getEnrollments(), metric.getRevenue()))
                .toList();
    }

    private static BigDecimal percent(long part, long whole) {
        if (whole <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(part).multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }
}
