package com.course_aura_hub.recommendation.service;

import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.mapper.CourseAssembler;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.recommendation.repository.RecommendationRepository;
import com.course_aura_hub.student.model.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationServiceImpl implements RecommendationService {

    // Matches no student, so the query falls back to popular courses
    private static final int NO_STUDENT = -1;

    private final RecommendationRepository recommendationRepository;
    private final CourseRepository courseRepository;
    private final CourseAssembler courseAssembler;
    private final LearnerAccountService learnerAccountService;

    @Override
    public List<CourseResponse> related(String slug, Integer limit) {
        Course course = courseRepository.findBySlug(slug)
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", slug));
        List<Integer> ids = recommendationRepository.findRelatedCourseIds(
                course.getId(), course.getCategory(), clamp(limit));
        log.debug("Related courses for slug={} count={}", slug, ids.size());
        return load(ids);
    }

    @Override
    public List<CourseResponse> forLearner(String email, Integer limit) {
        int studentId = learnerAccountService.findCurrentStudent(email)
                .map(Student::getId)
                .orElse(NO_STUDENT);
        List<Integer> ids = recommendationRepository.findCourseIdsForStudent(studentId, clamp(limit));
        log.debug("Recommendations for studentId={} count={}", studentId, ids.size());
        return load(ids);
    }

    private List<CourseResponse> load(List<Integer> ids) {
        Map<Integer, Course> byId = courseRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        // Keep the ranking order from the query; learners see the current (sale) price
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(course -> courseAssembler.toPublicResponse(course, null))
                .toList();
    }

    private static int clamp(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
