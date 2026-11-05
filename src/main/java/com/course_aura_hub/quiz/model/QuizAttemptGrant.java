package com.course_aura_hub.quiz.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Attempts staff added for one learner on one quiz, on top of the quiz's limit. */
@Entity
@Table(name = "quiz_attempt_grants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptGrant {

    @EmbeddedId
    private Id id;

    @Column(name = "extra_attempts", nullable = false)
    private Integer extraAttempts;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "student_id", nullable = false)
        private Integer studentId;

        @Column(name = "lesson_id", nullable = false)
        private Integer lessonId;
    }
}
