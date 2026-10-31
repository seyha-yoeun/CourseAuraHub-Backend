package com.course_aura_hub.certificate.repository;

import com.course_aura_hub.certificate.model.CourseCertificate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseCertificateRepository extends JpaRepository<CourseCertificate, Integer> {

    @EntityGraph(attributePaths = {"course"})
    List<CourseCertificate> findByStudentIdOrderByIssuedAtDesc(Integer studentId);

    @EntityGraph(attributePaths = {"student", "course"})
    Optional<CourseCertificate> findByCertificateNumber(String certificateNumber);

    boolean existsByStudentIdAndCourseId(Integer studentId, Integer courseId);

    long countByStudentId(Integer studentId);

    /**
     * Issues a certificate unless the learner already holds one for the course; safe under concurrent completions.
     *
     * @return 1 when a certificate was issued, 0 when one already existed
     */
    @Modifying
    @Query(value = """
            INSERT INTO course_certificates
                (certificate_number, student_id, course_id, learner_name, course_title, instructor, issued_at)
            VALUES (:certificateNumber, :studentId, :courseId, :learnerName, :courseTitle, :instructor, now())
            ON CONFLICT (student_id, course_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("certificateNumber") String certificateNumber,
            @Param("studentId") Integer studentId,
            @Param("courseId") Integer courseId,
            @Param("learnerName") String learnerName,
            @Param("courseTitle") String courseTitle,
            @Param("instructor") String instructor
    );

    /** Learners marked as having completed the course who have not been issued a certificate yet. */
    @Query("""
            select sc.student.id
            from StudentCourse sc
            where sc.course.id = :courseId
              and sc.completed = true
              and not exists (
                  select c.id from CourseCertificate c
                  where c.student.id = sc.student.id and c.course.id = sc.course.id
              )
            """)
    List<Integer> findCompletedStudentIdsWithoutCertificate(@Param("courseId") Integer courseId);
}
