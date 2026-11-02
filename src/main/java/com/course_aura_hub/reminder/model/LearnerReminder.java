package com.course_aura_hub.reminder.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** The last nudge a learner got about a course. */
@Entity
@Table(name = "learner_reminders")
@IdClass(LearnerReminder.Key.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnerReminder {

    @Id
    @Column(name = "student_id", nullable = false)
    private Integer studentId;

    @Id
    @Column(name = "course_id", nullable = false)
    private Integer courseId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    public record Key(Integer studentId, Integer courseId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
