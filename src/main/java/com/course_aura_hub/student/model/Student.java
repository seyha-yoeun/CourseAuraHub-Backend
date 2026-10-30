package com.course_aura_hub.student.model;

import com.course_aura_hub.auth.model.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
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
import java.util.Objects;

@Entity
@Table(name = "students")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "email", nullable = false, unique = true, columnDefinition = "text")
    private String email;

    @Column(name = "avatar", nullable = false, columnDefinition = "text")
    private String avatar;

    @Column(name = "total_spent", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalSpent;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;

    @Column(name = "last_active", nullable = false)
    private LocalDate lastActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private StudentStatus status;

    @Column(name = "country", nullable = false, columnDefinition = "text")
    private String country;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @Builder.Default
    private List<StudentCourse> courseEnrollments = new ArrayList<>();

    @PrePersist
    void onCreate() {
        LocalDate today = LocalDate.now();
        if (joinedAt == null) {
            joinedAt = today;
        }
        if (lastActive == null) {
            lastActive = today;
        }
    }

    public List<Integer> enrolledCourseIds() {
        if (courseEnrollments == null) {
            return List.of();
        }
        return courseEnrollments.stream()
                .map(StudentCourse::getCourse)
                .filter(Objects::nonNull)
                .map(course -> course.getId())
                .toList();
    }

    public List<Integer> completedCourseIds() {
        if (courseEnrollments == null) {
            return List.of();
        }
        return courseEnrollments.stream()
                .filter(StudentCourse::isCompleted)
                .map(StudentCourse::getCourse)
                .filter(Objects::nonNull)
                .map(course -> course.getId())
                .toList();
    }
}
