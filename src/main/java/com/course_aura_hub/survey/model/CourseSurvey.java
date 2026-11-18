package com.course_aura_hub.survey.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A course's feedback survey. The 0-10 recommendation question is built in and not stored with the others. */
@Entity
@Table(name = "course_surveys")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "course_id", nullable = false, unique = true, updatable = false)
    private Integer courseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, columnDefinition = "text")
    private SurveyTrigger trigger;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<SurveyQuestion> questions = new ArrayList<>();

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
