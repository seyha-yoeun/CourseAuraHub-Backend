package com.course_aura_hub.quiz.repository;

import com.course_aura_hub.quiz.model.QuizQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Integer> {

    @EntityGraph(attributePaths = "options")
    List<QuizQuestion> findByLessonIdOrderByPositionAsc(Integer lessonId);

    boolean existsByLessonId(Integer lessonId);

    void deleteByLessonId(Integer lessonId);
}
