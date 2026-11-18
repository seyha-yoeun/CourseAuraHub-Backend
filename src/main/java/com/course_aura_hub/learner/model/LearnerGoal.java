package com.course_aura_hub.learner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A learner's weekly goal and leaderboard choice. Learners without a row have no goal and are visible. */
@Entity
@Table(name = "learner_goals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnerGoal {

    @Id
    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Column(name = "weekly_lessons_target")
    private Integer weeklyLessonsTarget;

    @Builder.Default
    @Column(name = "leaderboard_visible", nullable = false)
    private boolean leaderboardVisible = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
