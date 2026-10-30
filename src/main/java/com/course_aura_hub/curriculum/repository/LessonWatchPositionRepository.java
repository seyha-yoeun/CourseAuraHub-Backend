package com.course_aura_hub.curriculum.repository;

import com.course_aura_hub.curriculum.model.LessonWatchPosition;
import com.course_aura_hub.curriculum.model.LessonWatchPositionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LessonWatchPositionRepository extends JpaRepository<LessonWatchPosition, LessonWatchPositionId> {

    // Players save every few seconds from possibly several tabs, so this must be a single atomic statement
    @Modifying
    @Query(value = """
            insert into lesson_watch_positions (student_id, lesson_id, position_seconds, updated_at)
            values (:studentId, :lessonId, :positionSeconds, now())
            on conflict (student_id, lesson_id)
            do update set position_seconds = excluded.position_seconds, updated_at = now()
            """, nativeQuery = true)
    void upsert(
            @Param("studentId") Integer studentId,
            @Param("lessonId") Integer lessonId,
            @Param("positionSeconds") int positionSeconds
    );

    List<LessonWatchPosition> findByStudentIdAndLessonIdIn(Integer studentId, Collection<Integer> lessonIds);

    void deleteByStudentIdAndLessonId(Integer studentId, Integer lessonId);
}
