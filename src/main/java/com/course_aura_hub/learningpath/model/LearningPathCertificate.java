package com.course_aura_hub.learningpath.model;

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

import java.time.Instant;

/**
 * Proof that a learner finished every course of a path. Name and title are copied at issue and the row is never
 * revoked, like a course certificate; {@code pathId} becomes null if the path is deleted.
 */
@Entity
@Table(name = "learning_path_certificates")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPathCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "certificate_number", nullable = false, unique = true, updatable = false, columnDefinition = "text")
    private String certificateNumber;

    @Column(name = "path_id")
    private Integer pathId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Column(name = "learner_name", nullable = false, updatable = false, columnDefinition = "text")
    private String learnerName;

    @Column(name = "path_title", nullable = false, updatable = false, columnDefinition = "text")
    private String pathTitle;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }
}
