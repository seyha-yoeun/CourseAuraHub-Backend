package com.course_aura_hub.survey.model;

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

/** One learner's answers. Staff only ever see these aggregated, never with who answered. */
@Entity
@Table(name = "survey_responses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "survey_id", nullable = false, updatable = false)
    private Integer surveyId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Column(name = "nps_score", nullable = false, updatable = false)
    private Integer npsScore;

    /** Question id to answer: a number for rating questions, text otherwise. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", nullable = false, updatable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> answers = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
