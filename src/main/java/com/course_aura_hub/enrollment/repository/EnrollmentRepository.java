package com.course_aura_hub.enrollment.repository;

import com.course_aura_hub.enrollment.model.Enrollment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Integer> {

    @EntityGraph(attributePaths = {"student", "course"})
    List<Enrollment> findAllByOrderByEnrolledAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"student", "course"})
    @Query("select e from Enrollment e where e.id = :id")
    Optional<Enrollment> findDetailedById(@Param("id") Integer id);

    boolean existsByStudentIdAndCourseId(Integer studentId, Integer courseId);

    long countByCourseId(Integer courseId);

    List<Enrollment> findByStudentId(Integer studentId);
}
