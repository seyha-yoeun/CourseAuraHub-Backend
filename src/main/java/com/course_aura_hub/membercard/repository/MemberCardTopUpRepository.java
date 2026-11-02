package com.course_aura_hub.membercard.repository;

import com.course_aura_hub.membercard.model.MemberCardTopUpRequest;
import com.course_aura_hub.membercard.model.MemberCardTopUpStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberCardTopUpRepository extends JpaRepository<MemberCardTopUpRequest, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MemberCardTopUpRequest r where r.id = :id")
    Optional<MemberCardTopUpRequest> findByIdForUpdate(@Param("id") Integer id);

    boolean existsByCardIdAndStatus(Integer cardId, MemberCardTopUpStatus status);

    long countByStatus(MemberCardTopUpStatus status);

    List<MemberCardTopUpRequest> findTop20ByCardIdOrderByCreatedAtDescIdDesc(Integer cardId);

    @EntityGraph(attributePaths = {"card", "card.student"})
    Page<MemberCardTopUpRequest> findByStatus(MemberCardTopUpStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"card", "card.student"})
    Page<MemberCardTopUpRequest> findAll(Pageable pageable);
}
