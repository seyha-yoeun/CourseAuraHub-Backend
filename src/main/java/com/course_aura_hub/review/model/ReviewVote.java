package com.course_aura_hub.review.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/** A learner marking a review helpful; at most one per learner and review. */
@Entity
@Table(name = "review_votes")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVote {

    @EmbeddedId
    private Id id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "review_id", nullable = false)
        private Integer reviewId;

        @Column(name = "student_id", nullable = false)
        private Integer studentId;
    }
}
