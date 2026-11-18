package com.course_aura_hub.payout.model;

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

import java.math.BigDecimal;
import java.time.Instant;

/** An instructor share agreed for one course, overriding the platform default. */
@Entity
@Table(name = "course_revenue_shares")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRevenueShare {

    @Id
    @Column(name = "course_id", nullable = false, updatable = false)
    private Integer courseId;

    @Column(name = "instructor_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal instructorPercent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
