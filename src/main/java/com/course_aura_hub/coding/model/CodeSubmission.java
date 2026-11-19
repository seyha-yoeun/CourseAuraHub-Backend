package com.course_aura_hub.coding.model;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** One graded run of a learner's code. */
@Entity
@Table(name = "code_submissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private Integer lessonId;

    @Column(name = "language", nullable = false, updatable = false, columnDefinition = "text")
    private String language;

    @Column(name = "code", nullable = false, updatable = false, columnDefinition = "text")
    private String code;

    @Column(name = "passed", nullable = false, updatable = false)
    private boolean passed;

    @Column(name = "passed_tests", nullable = false, updatable = false)
    private int passedTests;

    @Column(name = "total_tests", nullable = false, updatable = false)
    private int totalTests;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", nullable = false, updatable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> result = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
