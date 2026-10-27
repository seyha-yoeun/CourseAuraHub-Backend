package com.course_aura_hub.curriculum.repository;

import com.course_aura_hub.curriculum.model.Lesson;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Integer> {

    @EntityGraph(attributePaths = {"section", "section.course"})
    @Query("select l from Lesson l where l.id = :id")
    Optional<Lesson> findDetailedById(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"section"})
    @Query("select l from Lesson l where l.id = :id and l.section.course.id = :courseId")
    Optional<Lesson> findByIdAndCourseId(@Param("id") Integer id, @Param("courseId") Integer courseId);

    @Query("select count(l) from Lesson l where l.section.course.id = :courseId")
    long countByCourseId(@Param("courseId") Integer courseId);

    @Query("select coalesce(sum(l.durationMinutes), 0) from Lesson l where l.section.course.id = :courseId")
    long sumDurationMinutesByCourseId(@Param("courseId") Integer courseId);

    @Query("select coalesce(max(l.position), -1) from Lesson l where l.section.id = :sectionId")
    int findMaxPosition(@Param("sectionId") Integer sectionId);
}
