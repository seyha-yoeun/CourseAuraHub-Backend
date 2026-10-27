package com.course_aura_hub.curriculum.repository;

import com.course_aura_hub.curriculum.model.LessonProgress;
import com.course_aura_hub.curriculum.model.LessonProgressId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, LessonProgressId> {

    Optional<LessonProgress> findByStudentIdAndLessonId(Integer studentId, Integer lessonId);

    @Query("""
            select lp.lesson.id
            from LessonProgress lp
            where lp.student.id = :studentId
              and lp.lesson.section.course.id = :courseId
            """)
    List<Integer> findCompletedLessonIds(@Param("studentId") Integer studentId, @Param("courseId") Integer courseId);

    @Query("""
            select count(lp)
            from LessonProgress lp
            where lp.student.id = :studentId
              and lp.lesson.section.course.id = :courseId
            """)
    long countCompleted(@Param("studentId") Integer studentId, @Param("courseId") Integer courseId);

    @Query("""
            select l.section.course.id as courseId,
                   count(lp) as completedLessons,
                   max(lp.completedAt) as lastCompletedAt
            from LessonProgress lp
            join lp.lesson l
            where lp.student.id = :studentId
            group by l.section.course.id
            """)
    List<CourseProgressProjection> summarizeByStudent(@Param("studentId") Integer studentId);

    /**
     * Re-derives {@code student_courses.completed} for every learner of a course after its lesson set changed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE student_courses sc
            SET completed = (
                :totalLessons > 0 AND (
                    SELECT COUNT(*)
                    FROM lesson_progress lp
                    JOIN lessons l ON l.id = lp.lesson_id
                    JOIN course_sections s ON s.id = l.section_id
                    WHERE lp.student_id = sc.student_id AND s.course_id = sc.course_id
                ) >= :totalLessons
            )
            WHERE sc.course_id = :courseId
            """, nativeQuery = true)
    int recalculateCourseCompletion(@Param("courseId") Integer courseId, @Param("totalLessons") long totalLessons);
}
