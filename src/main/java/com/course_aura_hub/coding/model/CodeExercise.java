package com.course_aura_hub.coding.model;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The exercise of a CODE lesson. Examples are shown to learners; tests are hidden. */
@Entity
@Table(name = "code_exercises")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeExercise {

    @Id
    @Column(name = "lesson_id", nullable = false, updatable = false)
    private Integer lessonId;

    @Column(name = "language", nullable = false, columnDefinition = "text")
    private String language;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Column(name = "starter_code", nullable = false, columnDefinition = "text")
    private String starterCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "examples", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<TestCase> examples = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tests", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<TestCase> tests = new ArrayList<>();

    @Column(name = "time_limit_seconds", nullable = false, precision = 4, scale = 1)
    private BigDecimal timeLimitSeconds;

    @Column(name = "memory_limit_mb", nullable = false)
    private Integer memoryLimitMb;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
