package com.course_aura_hub.curriculum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lessons")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private CourseSection section;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "text")
    private LessonType type;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "content_url", columnDefinition = "text")
    private String contentUrl;

    @Column(name = "is_preview", nullable = false)
    private boolean preview;

    @Column(name = "position", nullable = false)
    private Integer position;

    // Score needed to pass, when this is a quiz with questions
    @Builder.Default
    @Column(name = "quiz_pass_percent", nullable = false)
    private Integer quizPassPercent = 70;

    /** Attempts a learner gets, before any granted by staff; null is unlimited. */
    @Column(name = "quiz_max_attempts")
    private Integer quizMaxAttempts;

    /** Wait after a failed attempt before the next one; null is none. */
    @Column(name = "quiz_retry_cooldown_minutes")
    private Integer quizRetryCooldownMinutes;

    @Column(name = "quiz_time_limit_minutes")
    private Integer quizTimeLimitMinutes;

    /** Questions drawn at random for each attempt; null asks every question. */
    @Column(name = "quiz_questions_per_attempt")
    private Integer quizQuestionsPerAttempt;

    @Column(name = "quiz_shuffle", nullable = false)
    private boolean quizShuffle;

    /** Timed, pooled and shuffled quizzes are taken through a server-side session. */
    public boolean quizNeedsSession() {
        return quizTimeLimitMinutes != null || quizQuestionsPerAttempt != null || quizShuffle;
    }
}
