package com.course_aura_hub.curriculum.service;

import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.curriculum.dto.CourseProgress;
import com.course_aura_hub.curriculum.dto.LessonProgressResponse;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.model.LessonProgress;
import com.course_aura_hub.curriculum.repository.CourseProgressProjection;
import com.course_aura_hub.curriculum.repository.LessonProgressRepository;
import com.course_aura_hub.curriculum.repository.LessonRepository;
import com.course_aura_hub.learner.service.LearnerAccountService;
import com.course_aura_hub.student.model.Student;
import com.course_aura_hub.student.model.StudentCourse;
import com.course_aura_hub.student.repository.StudentCourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonProgressServiceImpl implements LessonProgressService {

    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final LearnerAccountService learnerAccountService;

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
    @Transactional(readOnly = true)
    public Map<Integer, CourseProgressProjection> summarizeByStudent(Integer studentId) {
        return lessonProgressRepository.summarizeByStudent(studentId).stream()
                .collect(Collectors.toMap(CourseProgressProjection::getCourseId, Function.identity()));
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
