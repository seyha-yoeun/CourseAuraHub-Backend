package com.course_aura_hub.discussion.repository;

import com.course_aura_hub.discussion.model.LessonAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LessonAnswerRepository extends JpaRepository<LessonAnswer, Integer> {

    @EntityGraph(attributePaths = {"question", "question.lesson", "question.lesson.section", "question.lesson.section.course"})
    @Query("select a from LessonAnswer a where a.id = :id")
    Optional<LessonAnswer> findDetailedById(@Param("id") Integer id);
}
