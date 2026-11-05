package com.course_aura_hub.discussion.repository;

import com.course_aura_hub.discussion.model.LessonQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonQuestionRepository extends JpaRepository<LessonQuestion, Integer> {

    List<LessonQuestion> findByLessonIdAndHiddenFalseOrderByCreatedAtDescIdDesc(Integer lessonId);

    @EntityGraph(attributePaths = {"lesson", "lesson.section", "lesson.section.course"})
    @Query("select q from LessonQuestion q where q.id = :id")
    Optional<LessonQuestion> findDetailedById(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"lesson", "lesson.section", "lesson.section.course"})
    Page<LessonQuestion> findByResolvedFalseAndHiddenFalse(Pageable pageable);

    @EntityGraph(attributePaths = {"lesson", "lesson.section", "lesson.section.course"})
    @Query("select q from LessonQuestion q where q.resolved = false and q.hidden = false and q.lesson.section.course.ownerId = :ownerId")
    Page<LessonQuestion> findOpenByCourseOwner(@Param("ownerId") UUID ownerId, Pageable pageable);
}
