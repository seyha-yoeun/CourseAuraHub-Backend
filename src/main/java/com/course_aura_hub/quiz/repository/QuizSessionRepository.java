package com.course_aura_hub.quiz.repository;

import com.course_aura_hub.quiz.model.QuizSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {

    Optional<QuizSession> findByStudentIdAndLessonIdAndSubmittedAtIsNull(Integer studentId, Integer lessonId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from QuizSession s
            where s.studentId = :studentId and s.lessonId = :lessonId and s.submittedAt is null
            """)
    Optional<QuizSession> findOpenForUpdate(@Param("studentId") Integer studentId, @Param("lessonId") Integer lessonId);
}
