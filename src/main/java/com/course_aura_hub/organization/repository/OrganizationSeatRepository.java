package com.course_aura_hub.organization.repository;

import com.course_aura_hub.organization.model.OrganizationSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationSeatRepository extends JpaRepository<OrganizationSeat, Integer> {

    @EntityGraph(attributePaths = "course")
    List<OrganizationSeat> findByOrganizationIdOrderByIdAsc(Integer organizationId);

    Optional<OrganizationSeat> findByOrganizationIdAndCourseId(Integer organizationId, Integer courseId);

    /** Locked so two managers can't hand out the last seat twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrganizationSeat s join fetch s.course where s.organizationId = :organizationId and s.course.id = :courseId")
    Optional<OrganizationSeat> findForUpdate(@Param("organizationId") Integer organizationId, @Param("courseId") Integer courseId);
}
