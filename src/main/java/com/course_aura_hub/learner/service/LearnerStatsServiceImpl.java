package com.course_aura_hub.learner.service;

import com.course_aura_hub.certificate.repository.CourseCertificateRepository;
import com.course_aura_hub.curriculum.repository.LessonProgressRepository;
import com.course_aura_hub.learner.dto.AchievementResponse;
import com.course_aura_hub.learner.model.Achievement;
import com.course_aura_hub.review.repository.CourseReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearnerStatsServiceImpl implements LearnerStatsService {

    private final LessonProgressRepository lessonProgressRepository;
    private final CourseCertificateRepository certificateRepository;
    private final CourseReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public LearnerStats compute(Integer studentId, ZoneId zone, LocalDate today) {
        List<Instant> completionTimes = lessonProgressRepository.findCompletionTimes(studentId);
        Map<LocalDate, Long> lessonsByDay = completionTimes.stream()
                .collect(Collectors.groupingBy(completedAt -> LocalDate.ofInstant(completedAt, zone), Collectors.counting()));

        return new LearnerStats(
                completionTimes.size(),
                lessonProgressRepository.sumCompletedMinutes(studentId),
                certificateRepository.countByStudentId(studentId),
                reviewRepository.countByStudentId(studentId),
                LearningStreakCalculator.calculate(lessonsByDay.keySet(), today),
                lessonsByDay
        );
    }

    @Override
    public List<AchievementResponse> achievements(LearnerStats stats) {
        return Arrays.stream(Achievement.values())
                .map(achievement -> {
                    long value = stats.value(achievement.getMetric());
                    return new AchievementResponse(
                            achievement.name(),
                            achievement.getTitle(),
                            achievement.getDescription(),
                            achievement.getMetric().name().toLowerCase(Locale.ROOT),
                            Math.min(value, achievement.getTarget()),
                            achievement.getTarget(),
                            value >= achievement.getTarget()
                    );
                })
                .toList();
    }
}
