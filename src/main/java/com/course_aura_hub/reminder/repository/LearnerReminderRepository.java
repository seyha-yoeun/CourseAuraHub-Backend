package com.course_aura_hub.reminder.repository;

import com.course_aura_hub.reminder.model.LearnerReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface LearnerReminderRepository extends JpaRepository<LearnerReminder, LearnerReminder.Key> {

    /** One unfinished course per learner, for people who have been quiet since {@code quietSince}. */
    @Query(nativeQuery = true, value = """
            select distinct on (sc.student_id)
                   sc.student_id as studentId, c.id as courseId, c.slug as slug, c.title as title
            from student_courses sc
            join students s on s.id = sc.student_id
            join courses c on c.id = sc.course_id
            where not sc.completed
              and s.reminders_enabled
              and s.user_id is not null
              and c.status = 'PUBLISHED'
              and not exists (
                  select 1 from enrollments e
                  where e.student_id = sc.student_id and e.course_id = sc.course_id and e.enrolled_at >= :quietSinceDate)
              and not exists (
                  select 1 from lesson_progress lp
                  join lessons l on l.id = lp.lesson_id
                  join course_sections cs on cs.id = l.section_id
                  where lp.student_id = sc.student_id and cs.course_id = sc.course_id and lp.completed_at >= :quietSince)
              and not exists (
                  select 1 from lesson_watch_positions wp
                  join lessons l on l.id = wp.lesson_id
                  join course_sections cs on cs.id = l.section_id
                  where wp.student_id = sc.student_id and cs.course_id = sc.course_id and wp.updated_at >= :quietSince)
              and not exists (
                  select 1 from learner_reminders r
                  where r.student_id = sc.student_id and r.sent_at >= :remindedSince)
            order by sc.student_id, c.id
            limit :limit
            """)
    List<DueReminder> findDue(
            @Param("quietSince") Instant quietSince,
            @Param("quietSinceDate") LocalDate quietSinceDate,
            @Param("remindedSince") Instant remindedSince,
            @Param("limit") int limit
    );

    interface DueReminder {
        Integer getStudentId();

        Integer getCourseId();

        String getSlug();

        String getTitle();
    }
}
