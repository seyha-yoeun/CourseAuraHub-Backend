package com.course_aura_hub.quiz.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One graded try at a quiz. Rows stay when a quiz is edited, so a pass is never taken away. */
@Entity
@Table(name = "quiz_attempts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private Integer lessonId;

    @Column(name = "correct_count", nullable = false, updatable = false)
    private Integer correctCount;

    @Column(name = "total_count", nullable = false, updatable = false)
    private Integer totalCount;

    @Column(name = "score_percent", nullable = false, updatable = false)
    private Integer scorePercent;

    @Column(name = "passed", nullable = false, updatable = false)
    private boolean passed;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @PrePersist
    void onCreate() {
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }
}
