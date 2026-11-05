package com.course_aura_hub.instructor.repository;

import com.course_aura_hub.course.model.Course;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only aggregates for a course's analytics. Only counts and averages leave the database, never learner rows.
 * Lesson progress counts only learners still enrolled, so refunded learners don't inflate the funnel.
 */
@org.springframework.stereotype.Repository
public interface InstructorAnalyticsRepository extends Repository<Course, Integer> {

    interface LessonStats {
        Integer getLessonId();
        String getSectionTitle();
        String getTitle();
        String getType();
        long getCompletedLearners();
        long getWatchingLearners();
        BigDecimal getAverageWatchSeconds();
        long getQuizAttempts();
        long getQuizLearners();
        long getQuizPassedLearners();
        BigDecimal getQuizAverageScore();
    }

    interface LearnerTotals {
        long getEnrolled();
        long getCompleted();
    }

    interface RatingCount {
        int getRating();
        long getReviews();
    }

    interface SubmissionStats {
        long getPending();
        long getGraded();
        BigDecimal getAverageScore();
    }

    @Query(value = """
            select l.id as lessonId, s.title as sectionTitle, l.title as title, l.type as type,
                   (select count(*) from lesson_progress lp
                    join student_courses sc on sc.student_id = lp.student_id and sc.course_id = :courseId
                    where lp.lesson_id = l.id) as completedLearners,
                   (select count(*) from lesson_watch_positions w
                    join student_courses sc on sc.student_id = w.student_id and sc.course_id = :courseId
                    where w.lesson_id = l.id) as watchingLearners,
                   (select avg(w.position_seconds) from lesson_watch_positions w
                    join student_courses sc on sc.student_id = w.student_id and sc.course_id = :courseId
                    where w.lesson_id = l.id) as averageWatchSeconds,
                   (select count(*) from quiz_attempts q where q.lesson_id = l.id) as quizAttempts,
                   (select count(distinct q.student_id) from quiz_attempts q where q.lesson_id = l.id) as quizLearners,
                   (select count(distinct q.student_id) from quiz_attempts q
                    where q.lesson_id = l.id and q.passed) as quizPassedLearners,
                   (select avg(q.score_percent) from quiz_attempts q where q.lesson_id = l.id) as quizAverageScore
            from lessons l
            join course_sections s on s.id = l.section_id
            where s.course_id = :courseId
            order by s.position, s.id, l.position, l.id
            """, nativeQuery = true)
    List<LessonStats> findLessonStats(@Param("courseId") Integer courseId);

    @Query(value = """
            select count(*) as enrolled, count(*) filter (where completed) as completed
            from student_courses where course_id = :courseId
            """, nativeQuery = true)
    LearnerTotals findLearnerTotals(@Param("courseId") Integer courseId);

    @Query(value = """
            select rating as rating, count(*) as reviews
            from course_reviews where course_id = :courseId
            group by rating
            """, nativeQuery = true)
    List<RatingCount> findRatingCounts(@Param("courseId") Integer courseId);

    @Query(value = """
            select count(*) filter (where sub.status = 'SUBMITTED') as pending,
                   count(*) filter (where sub.status = 'GRADED') as graded,
                   avg(sub.score) filter (where sub.status = 'GRADED') as averageScore
            from assignment_submissions sub
            join assignments a on a.id = sub.assignment_id
            join lessons l on l.id = a.lesson_id
            join course_sections s on s.id = l.section_id
            where s.course_id = :courseId
            """, nativeQuery = true)
    SubmissionStats findSubmissionStats(@Param("courseId") Integer courseId);
}
