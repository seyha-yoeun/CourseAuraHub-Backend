package com.course_aura_hub.course.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "course_monthly_metrics")
@IdClass(CourseMonthlyMetricId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseMonthlyMetric {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Id
    @Column(name = "month", nullable = false)
    private Short month;

    @Column(name = "enrollments", nullable = false)
    private Integer enrollments;

    @Column(name = "revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenue;
}
