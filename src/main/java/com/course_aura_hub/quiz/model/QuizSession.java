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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** One attempt in progress: the questions it drew, in the order shown, and its deadline. */
@Entity
@Table(name = "quiz_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private Integer lessonId;

    /** Comma-separated question ids. */
    @Column(name = "question_ids", nullable = false, updatable = false, columnDefinition = "text")
    private String questionIds;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /** Null when the quiz has no time limit. */
    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public List<Integer> questionIdList() {
        return Arrays.stream(questionIds.split(",")).map(String::trim).map(Integer::valueOf).toList();
    }

    public static String join(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
