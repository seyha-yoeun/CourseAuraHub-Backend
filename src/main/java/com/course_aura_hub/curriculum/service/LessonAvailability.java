package com.course_aura_hub.curriculum.service;

import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.curriculum.model.CourseSection;
import com.course_aura_hub.curriculum.model.Lesson;
import com.course_aura_hub.curriculum.repository.CourseSectionRepository;
import com.course_aura_hub.curriculum.repository.LessonProgressRepository;
import com.course_aura_hub.enrollment.model.Enrollment;
import com.course_aura_hub.enrollment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pacing rules for enrolled learners. A lesson is locked while its drip date is in the future, or, in a sequential
 * course, while an earlier lesson is incomplete. Preview lessons and lessons the learner already completed are
 * never locked. Course staff are not subject to pacing; callers decide who that is.
 */
@Component
@RequiredArgsConstructor
public class LessonAvailability {

    private final CourseSectionRepository sectionRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;

    /**
     * @param orderedLessons every lesson of the course in curriculum order
     * @param enrolledOn     the learner's enrollment date, or null when unknown (drip then never locks)
     * @return locks by lesson id; open lessons are absent
     */
    public static Map<Integer, LessonLock> locks(
            Course course,
            List<Lesson> orderedLessons,
            Set<Integer> completedIds,
            LocalDate enrolledOn,
            LocalDate today
    ) {
        Map<Integer, LessonLock> locks = new HashMap<>();
        boolean earlierIncomplete = false;
        for (Lesson lesson : orderedLessons) {
            boolean completed = completedIds.contains(lesson.getId());
            if (!completed && !lesson.isPreview()) {
                LocalDate opensOn = opensOn(lesson, enrolledOn);
                if (opensOn != null && opensOn.isAfter(today)) {
                    locks.put(lesson.getId(), new LessonLock(LessonLock.DRIP, opensOn));
                } else if (course.isSequentialLessons() && earlierIncomplete) {
                    locks.put(lesson.getId(), new LessonLock(LessonLock.SEQUENCE, null));
                }
            }
            earlierIncomplete |= !completed;
        }
        return locks;
    }

    /** Locks for one enrolled learner across a course, given the course's sections in order. */
    @Transactional(readOnly = true)
    public Map<Integer, LessonLock> locksFor(Course course, Integer studentId, List<CourseSection> sections,
                                             Set<Integer> completedIds) {
        List<Lesson> lessons = sections.stream().flatMap(section -> section.getLessons().stream()).toList();
        if (!hasPacing(course, lessons)) {
            return Map.of();
        }
        return locks(course, lessons, completedIds, enrolledOn(studentId, course), LocalDate.now());
    }

    /** Rejects work on a lesson the learner can't open yet, such as completing it or taking its quiz. */
    @Transactional(readOnly = true)
    public void requireAvailable(Lesson lesson, Integer studentId) {
        Course course = lesson.getSection().getCourse();
        if (!course.isSequentialLessons() && !isDripped(lesson)) {
            return;
        }
        List<CourseSection> sections = sectionRepository.findByCourseIdOrderByPositionAscIdAsc(course.getId());
        Set<Integer> completed = Set.copyOf(lessonProgressRepository.findCompletedLessonIds(studentId, course.getId()));
        LessonLock lock = locksFor(course, studentId, sections, completed).get(lesson.getId());
        if (lock == null) {
            return;
        }
        throw new ForbiddenActionException(LessonLock.DRIP.equals(lock.reason())
                ? "This lesson opens on " + lock.availableOn()
                : "Complete the earlier lessons first");
    }

    private LocalDate enrolledOn(Integer studentId, Course course) {
        return enrollmentRepository.findByStudentIdAndCourseId(studentId, course.getId())
                .map(Enrollment::getEnrolledAt)
                .orElse(null);
    }

    private static boolean hasPacing(Course course, List<Lesson> lessons) {
        return course.isSequentialLessons() || lessons.stream().anyMatch(LessonAvailability::isDripped);
    }

    private static boolean isDripped(Lesson lesson) {
        return lesson.getReleaseAfterDays() != null && lesson.getReleaseAfterDays() > 0;
    }

    private static LocalDate opensOn(Lesson lesson, LocalDate enrolledOn) {
        return isDripped(lesson) && enrolledOn != null ? enrolledOn.plusDays(lesson.getReleaseAfterDays()) : null;
    }
}
