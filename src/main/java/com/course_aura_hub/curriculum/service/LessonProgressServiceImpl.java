package com.course_aura_hub.curriculum.service;

import com.course_aura_hub.certificate.service.CertificateService;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.curriculum.dto.CourseProgress;
import com.course_aura_hub.curriculum.dto.LessonPositionResponse;
import com.course_aura_hub.curriculum.dto.LessonProgressResponse;
import com.course_aura_hub.curriculum.dto.NextLessonResponse;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.model.LessonProgress;
import com.course_aura_hub.curriculum.repository.CourseProgressProjection;
import com.course_aura_hub.curriculum.repository.LessonProgressRepository;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentCourse;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.curriculum.model.LessonType;
import com.course_aura_hub.curriculum.model.LessonWatchPosition;
import com.course_aura_hub.curriculum.repository.LessonWatchPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonProgressServiceImpl implements LessonProgressService {

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final LessonWatchPositionRepository lessonWatchPositionRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;
    private final CertificateService certificateService;

    @Override
    @Transactional
    public LessonProgressResponse markComplete(String email, Integer lessonId) {
        Lesson lesson = findLesson(lessonId);
        Course course = lesson.getSection().getCourse();
        StudentCourse studentCourse = requireEnrollment(email, course);
        Student student = studentCourse.getStudent();

        if (lessonProgressRepository.findByStudentIdAndLessonId(student.getId(), lessonId).isEmpty()) {
            lessonProgressRepository.save(LessonProgress.builder()
                    .student(student)
                    .lesson(lesson)
                    .build());
            // A finished video starts over when rewatched
            lessonWatchPositionRepository.deleteByStudentIdAndLessonId(student.getId(), lessonId);
            log.info("Marked lesson complete lessonId={} studentId={} courseId={}",
                    lessonId, student.getId(), course.getId());
        }

        return buildResponse(lessonId, true, course, studentCourse);
    }

    @Override
    @Transactional
    public LessonProgressResponse markIncomplete(String email, Integer lessonId) {
        Lesson lesson = findLesson(lessonId);
        Course course = lesson.getSection().getCourse();
        StudentCourse studentCourse = requireEnrollment(email, course);
        Student student = studentCourse.getStudent();

        lessonProgressRepository.findByStudentIdAndLessonId(student.getId(), lessonId).ifPresent(progress -> {
            lessonProgressRepository.delete(progress);
            lessonProgressRepository.flush();
            log.info("Marked lesson incomplete lessonId={} studentId={} courseId={}",
                    lessonId, student.getId(), course.getId());
        });

        return buildResponse(lessonId, false, course, studentCourse);
    }

    @Override
    @Transactional
    public LessonPositionResponse savePosition(String email, Integer lessonId, int positionSeconds) {
        Lesson lesson = findLesson(lessonId);
        if (lesson.getType() != LessonType.VIDEO) {
            throw new BusinessRuleException("Only video lessons have a playback position");
        }
        Student student = requireEnrollment(email, lesson.getSection().getCourse()).getStudent();
        lessonWatchPositionRepository.upsert(student.getId(), lessonId, positionSeconds);
        return new LessonPositionResponse(lessonId, positionSeconds);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, CourseProgressProjection> summarizeByStudent(Integer studentId) {
        return lessonProgressRepository.summarizeByStudent(studentId).stream()
                .collect(Collectors.toMap(CourseProgressProjection::getCourseId, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, NextLessonResponse> findNextLessons(Integer studentId, Collection<Integer> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        Set<Integer> completedIds = Set.copyOf(lessonProgressRepository.findAllCompletedLessonIds(studentId));
        Map<Integer, Lesson> nextLessons = new HashMap<>();
        // Lessons arrive grouped by course in curriculum order, so the first incomplete one per course wins
        for (Lesson lesson : lessonRepository.findInCurriculumOrderByCourseIds(courseIds)) {
            if (!completedIds.contains(lesson.getId())) {
                nextLessons.putIfAbsent(lesson.getSection().getCourse().getId(), lesson);
            }
        }

        List<Integer> lessonIds = nextLessons.values().stream().map(Lesson::getId).toList();
        Map<Integer, Integer> positions = lessonWatchPositionRepository
                .findByStudentIdAndLessonIdIn(studentId, lessonIds).stream()
                .collect(Collectors.toMap(LessonWatchPosition::getLessonId, LessonWatchPosition::getPositionSeconds));

        Map<Integer, NextLessonResponse> nextByCourse = new HashMap<>();
        nextLessons.forEach((courseId, lesson) -> nextByCourse.put(courseId, new NextLessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getType().apiValue(),
                lesson.getDurationMinutes(),
                lesson.getSection().getId(),
                lesson.getSection().getTitle(),
                positions.get(lesson.getId())
        )));
        return nextByCourse;
    }

    private LessonProgressResponse buildResponse(
            Integer lessonId,
            boolean lessonCompleted,
            Course course,
            StudentCourse studentCourse
    ) {
        Student student = studentCourse.getStudent();
        long total = lessonRepository.countByCourseId(course.getId());
        long done = lessonProgressRepository.countCompleted(student.getId(), course.getId());
        boolean courseCompleted = total > 0 && done >= total;

        if (studentCourse.isCompleted() != courseCompleted) {
            studentCourse.setCompleted(courseCompleted);
            log.info("Course completion changed studentId={} courseId={} completed={}",
                    student.getId(), course.getId(), courseCompleted);
        }
        if (courseCompleted) {
            certificateService.issueIfAbsent(student.getId(), course.getId());
        }
        student.setLastActive(LocalDate.now());

        return new LessonProgressResponse(
                lessonId,
                course.getSlug(),
                lessonCompleted,
                CourseProgress.of(done, total, courseCompleted)
        );
    }

    private Lesson findLesson(Integer lessonId) {
        return lessonRepository.findDetailedById(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
    }

    private StudentCourse requireEnrollment(String email, Course course) {
        return learnerAccountService.findCurrentStudent(email)
                .flatMap(student -> studentCourseRepository.findByStudentIdAndCourseId(student.getId(), course.getId()))
                .orElseThrow(() -> new ForbiddenActionException("You must be enrolled in this course to track progress"));
    }
}
