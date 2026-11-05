package com.course_aura_hub.curriculum.service;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.curriculum.model.Lesson;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LessonAvailabilityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void sequentialCourse_locksEverythingAfterTheFirstIncompleteLesson() {
        Course course = course(true);
        List<Lesson> lessons = List.of(lesson(1, null, false), lesson(2, null, false), lesson(3, null, false));

        Map<Integer, LessonLock> locks = LessonAvailability.locks(course, lessons, Set.of(1), TODAY, TODAY);

        assertThat(locks).containsOnlyKeys(3);
        assertThat(locks.get(3).reason()).isEqualTo(LessonLock.SEQUENCE);
    }

    @Test
    void completedAndPreviewLessonsAreNeverLocked() {
        Course course = course(true);
        List<Lesson> lessons = List.of(lesson(1, null, false), lesson(2, 30, true), lesson(3, null, false));

        assertThat(LessonAvailability.locks(course, lessons, Set.of(3), TODAY, TODAY)).isEmpty();
    }

    @Test
    void drip_opensOnEnrollmentDatePlusDelay() {
        Course course = course(false);
        List<Lesson> lessons = List.of(lesson(1, 7, false), lesson(2, 3, false));
        LocalDate enrolled = TODAY.minusDays(3);

        Map<Integer, LessonLock> locks = LessonAvailability.locks(course, lessons, Set.of(), enrolled, TODAY);

        assertThat(locks).containsOnlyKeys(1);
        assertThat(locks.get(1)).isEqualTo(new LessonLock(LessonLock.DRIP, enrolled.plusDays(7)));
    }

    @Test
    void unknownEnrollmentDateNeverDrips() {
        assertThat(LessonAvailability.locks(course(false), List.of(lesson(1, 7, false)), Set.of(), null, TODAY)).isEmpty();
    }

    private static Course course(boolean sequential) {
        Course course = new Course();
        course.setSequentialLessons(sequential);
        return course;
    }

    private static Lesson lesson(int id, Integer releaseAfterDays, boolean preview) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setReleaseAfterDays(releaseAfterDays);
        lesson.setPreview(preview);
        return lesson;
    }
}
