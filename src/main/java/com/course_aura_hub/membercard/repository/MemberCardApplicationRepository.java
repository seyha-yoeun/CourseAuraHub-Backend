package com.course_aura_hub.membercard.repository;

import com.course_aura_hub.membercard.model.MemberCardApplication;
import com.course_aura_hub.membercard.model.MemberCardApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberCardApplicationRepository extends JpaRepository<MemberCardApplication, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MemberCardApplication a where a.id = :id")
    Optional<MemberCardApplication> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MemberCardApplication a where a.student.id = :studentId and a.status = :status")
    Optional<MemberCardApplication> findByStudentIdAndStatusForUpdate(
            @Param("studentId") Integer studentId,
            @Param("status") MemberCardApplicationStatus status
    );

    Optional<MemberCardApplication> findFirstByStudentIdOrderByCreatedAtDescIdDesc(Integer studentId);

    boolean existsByStudentIdAndStatus(Integer studentId, MemberCardApplicationStatus status);

    @EntityGraph(attributePaths = "student")
    Page<MemberCardApplication> findByStatus(MemberCardApplicationStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "student")
    Page<MemberCardApplication> findAll(Pageable pageable);
}
