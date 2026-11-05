package com.course_aura_hub.prerequisite.service;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.service.CourseAccess;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.prerequisite.dto.PrerequisiteDtos;
import com.course_aura_hub.prerequisite.model.CoursePrerequisite;
import com.course_aura_hub.prerequisite.repository.CoursePrerequisiteRepository;
import com.course_aura_hub.student.model.StudentCourse;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrerequisiteServiceImpl implements PrerequisiteService {

    private final CoursePrerequisiteRepository repository;
    private final CourseRepository courseRepository;
    private final CourseAccess courseAccess;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;

    @Override
    @Transactional(readOnly = true)
    public PrerequisiteDtos.Requirements find(String slug, String viewerEmail) {
        Course course = courseRepository.findBySlug(slug)
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", slug));
        Set<Integer> completed = viewerEmail == null ? null : learnerAccountService.findCurrentStudent(viewerEmail)
                .map(student -> completedCourseIds(student.getId()))
                .orElse(Set.of());
        List<PrerequisiteDtos.Prerequisite> items = repository.findByIdCourseIdOrderByPositionAsc(course.getId()).stream()
                .map(item -> new PrerequisiteDtos.Prerequisite(item.getRequiredCourse().getSlug(), item.getRequiredCourse().getTitle(),
                        item.isHard(), completed == null ? null : completed.contains(item.getId().getRequiredCourseId())))
                .toList();
        boolean canEnroll = items.stream().noneMatch(item -> item.hard() && Boolean.FALSE.equals(item.completed()));
        return new PrerequisiteDtos.Requirements(canEnroll, items);
    }

    @Override
    @Transactional
    public List<PrerequisiteDtos.Prerequisite> replace(Actor actor, String slug, List<PrerequisiteDtos.Item> items) {
        Course course = courseAccess.requireCourse(slug);
        courseAccess.requireManager(course, actor);

        Map<String, PrerequisiteDtos.Item> bySlug = new LinkedHashMap<>();
        items.forEach(item -> bySlug.putIfAbsent(item.courseSlug().trim(), item));
        List<Course> required = new ArrayList<>();
        for (String requiredSlug : bySlug.keySet()) {
            Course other = courseRepository.findBySlug(requiredSlug)
                    .orElseThrow(() -> ResourceNotFoundException.of("Course", requiredSlug));
            if (other.getId().equals(course.getId())) {
                throw new BusinessRuleException("A course can't require itself");
            }
            required.add(other);
        }
        requireNoCycle(course.getId(), required.stream().map(Course::getId).toList());

        repository.deleteByCourseId(course.getId());
        repository.flush();
        for (int i = 0; i < required.size(); i++) {
            Course other = required.get(i);
            Boolean hard = bySlug.get(other.getSlug()).hard();
            repository.save(CoursePrerequisite.builder()
                    .id(new CoursePrerequisite.Id(course.getId(), other.getId()))
                    .requiredCourse(other)
                    .hard(hard == null || hard)
                    .position(i)
                    .build());
        }
        log.info("Prerequisites set courseId={} count={}", course.getId(), required.size());
        return required.stream()
                .map(other -> new PrerequisiteDtos.Prerequisite(other.getSlug(), other.getTitle(),
                        bySlug.get(other.getSlug()).hard() == null || bySlug.get(other.getSlug()).hard(), null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void requireMet(Integer studentId, Integer courseId) {
        List<CoursePrerequisite> hard = repository.findByIdCourseIdOrderByPositionAsc(courseId).stream()
                .filter(CoursePrerequisite::isHard).toList();
        if (hard.isEmpty()) {
            return;
        }
        Set<Integer> completed = completedCourseIds(studentId);
        List<String> missing = hard.stream()
                .filter(item -> !completed.contains(item.getId().getRequiredCourseId()))
                .map(item -> item.getRequiredCourse().getTitle())
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("Complete " + String.join(", ", missing) + " before taking this course");
        }
    }

    private Set<Integer> completedCourseIds(Integer studentId) {
        return studentCourseRepository.findByStudentIdWithCourse(studentId).stream()
                .filter(StudentCourse::isCompleted)
                .map(link -> link.getCourse().getId())
                .collect(Collectors.toSet());
    }

    /** Adding course -> required must not let any required course lead back to the course. */
    private void requireNoCycle(Integer courseId, List<Integer> requiredIds) {
        Map<Integer, Set<Integer>> edges = new HashMap<>();
        for (Object[] edge : repository.findAllEdges()) {
            Integer from = (Integer) edge[0];
            if (!from.equals(courseId)) {
                edges.computeIfAbsent(from, key -> new HashSet<>()).add((Integer) edge[1]);
            }
        }
        Deque<Integer> stack = new ArrayDeque<>(requiredIds);
        Set<Integer> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            Integer current = stack.pop();
            if (current.equals(courseId)) {
                throw new BusinessRuleException("These prerequisites would make courses require each other");
            }
            if (seen.add(current)) {
                stack.addAll(edges.getOrDefault(current, Set.of()));
            }
        }
    }
}
