package com.course_aura_hub.certificate.model;

import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.student.model.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;

/**
 * Proof that a learner completed a course. Rows are inserted through
 * {@code CourseCertificateRepository#insertIfAbsent} and never updated.
 */
@Entity
@Table(name = "course_certificates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "certificate_number", nullable = false, unique = true, updatable = false, columnDefinition = "text")
    private String certificateNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private Course course;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;
}
