package com.course_aura_hub.discussion.model;

import com.course_aura_hub.curriculum.model.Lesson;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lesson_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    private Lesson lesson;

    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    @Column(name = "author_name", nullable = false, updatable = false, columnDefinition = "text")
    private String authorName;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc, id asc")
    @BatchSize(size = 50)
    @Builder.Default
    private List<LessonAnswer> answers = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
