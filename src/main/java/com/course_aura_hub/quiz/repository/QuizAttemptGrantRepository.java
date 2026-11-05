package com.course_aura_hub.quiz.repository;

import com.course_aura_hub.quiz.model.QuizAttemptGrant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptGrantRepository extends JpaRepository<QuizAttemptGrant, QuizAttemptGrant.Id> {
}
