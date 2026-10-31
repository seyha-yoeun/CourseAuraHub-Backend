package com.course_aura_hub.quiz.repository;

import com.course_aura_hub.quiz.model.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Integer> {

    boolean existsByStudentIdAndLessonIdAndPassedTrue(Integer studentId, Integer lessonId);

    long countByStudentIdAndLessonId(Integer studentId, Integer lessonId);

    @Query("select max(a.scorePercent) from QuizAttempt a where a.studentId = :studentId and a.lessonId = :lessonId")
    Integer findBestScore(@Param("studentId") Integer studentId, @Param("lessonId") Integer lessonId);
}
