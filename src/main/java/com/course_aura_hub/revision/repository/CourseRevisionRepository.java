package com.course_aura_hub.revision.repository;

import com.course_aura_hub.revision.model.CourseRevision;
import com.course_aura_hub.revision.model.RevisionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRevisionRepository extends JpaRepository<CourseRevision, Integer> {

    Optional<CourseRevision> findByCourseIdAndStatus(Integer courseId, RevisionStatus status);

    List<CourseRevision> findByCourseIdOrderByCreatedAtDescIdDesc(Integer courseId);

    List<CourseRevision> findByCourseIdAndStatusOrderByReviewedAtDescIdDesc(Integer courseId, RevisionStatus status);

    @EntityGraph(attributePaths = "course")
    List<CourseRevision> findByStatusOrderByUpdatedAtAscIdAsc(RevisionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CourseRevision r join fetch r.course where r.id = :id")
    Optional<CourseRevision> findByIdForUpdate(@Param("id") Integer id);
}
