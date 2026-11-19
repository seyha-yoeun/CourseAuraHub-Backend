package com.course_aura_hub.subscription.repository;

import com.course_aura_hub.subscription.model.Subscription;
import com.course_aura_hub.subscription.model.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    Optional<Subscription> findFirstByStudentIdAndStatusIn(Integer studentId, Collection<SubscriptionStatus> statuses);

    Optional<Subscription> findFirstByStudentIdOrderByCreatedAtDescIdDesc(Integer studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> findByIdForUpdate(@Param("id") Integer id);

    @Query("select s.id from Subscription s where s.status = :status and s.currentPeriodEnd <= :before")
    List<Integer> findIdsEndedBefore(@Param("status") SubscriptionStatus status, @Param("before") Instant before);

    @Query("select s.id from Subscription s where s.status = :status and s.createdAt <= :before")
    List<Integer> findIdsCreatedBefore(@Param("status") SubscriptionStatus status, @Param("before") Instant before);

    Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable);
}
