package com.course_aura_hub.payout.repository;

import com.course_aura_hub.payout.model.PayoutRequest;
import com.course_aura_hub.payout.model.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Integer> {

    List<PayoutRequest> findByInstructorIdOrderByCreatedAtDescIdDesc(UUID instructorId);

    boolean existsByInstructorIdAndStatusIn(UUID instructorId, Collection<PayoutStatus> statuses);

    Page<PayoutRequest> findByStatus(PayoutStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayoutRequest p where p.id = :id")
    Optional<PayoutRequest> findByIdForUpdate(@Param("id") Integer id);
}
