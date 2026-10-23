package com.course_aura_hub.course.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "slug", nullable = false, unique = true, columnDefinition = "text")
    private String slug;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "instructor", nullable = false, columnDefinition = "text")
    private String instructor;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private CourseStatus status;

    @Column(name = "category", nullable = false, columnDefinition = "text")
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, columnDefinition = "text")
    private CourseLevel level;

    @Column(name = "thumbnail", nullable = false, columnDefinition = "text")
    private String thumbnail;

    @Column(name = "rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "total_students", nullable = false)
    private Integer totalStudents;

    @Column(name = "total_lessons", nullable = false)
    private Integer totalLessons;

    @Column(name = "total_hours", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalHours;

    @Column(name = "revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenue;

    @Column(name = "completion_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal completionRate;

    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDate updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "course_tags", joinColumns = @JoinColumn(name = "course_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "tag", nullable = false, columnDefinition = "text")
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @Builder.Default
    private List<CourseMonthlyMetric> monthlyMetrics = new ArrayList<>();

    @PrePersist
    void onCreate() {
        LocalDate today = LocalDate.now();
        if (createdAt == null) {
            createdAt = today;
        }
        if (updatedAt == null) {
            updatedAt = today;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDate.now();
    }

    public void addMonthlyMetric(CourseMonthlyMetric metric) {
        metric.setCourse(this);
        monthlyMetrics.add(metric);
    }

    public void replaceTags(List<String> newTags) {
        tags.clear();
        if (newTags != null) {
            tags.addAll(newTags);
        }
    }
}
