package com.course_aura_hub.assignment.repository;

import com.course_aura_hub.assignment.model.AssignmentSubmission;
import com.course_aura_hub.assignment.model.SubmissionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Integer> {

    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(Integer assignmentId, Integer studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AssignmentSubmission s where s.id = :id")
    Optional<AssignmentSubmission> findByIdForUpdate(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"student", "assignment", "assignment.lesson", "assignment.lesson.section", "assignment.lesson.section.course"})
    Page<AssignmentSubmission> findByStatus(SubmissionStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "assignment", "assignment.lesson", "assignment.lesson.section", "assignment.lesson.section.course"})
    @Query("""
            select s from AssignmentSubmission s
            where s.status = :status and s.assignment.lesson.section.course.ownerId = :ownerId
            """)
    Page<AssignmentSubmission> findByStatusAndCourseOwner(
            @Param("status") SubmissionStatus status,
            @Param("ownerId") UUID ownerId,
            Pageable pageable
    );
}
