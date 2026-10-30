package com.course_aura_hub.curriculum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "lesson_watch_positions")
@IdClass(LessonWatchPositionId.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LessonWatchPosition {

    @Id
    @Column(name = "student_id", nullable = false)
    private Integer studentId;

    @Id
    @Column(name = "lesson_id", nullable = false)
    private Integer lessonId;

    @Column(name = "position_seconds", nullable = false)
    private Integer positionSeconds;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
