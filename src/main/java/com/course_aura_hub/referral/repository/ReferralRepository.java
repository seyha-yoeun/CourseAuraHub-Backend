package com.course_aura_hub.referral.repository;

import com.course_aura_hub.referral.model.Referral;
import com.course_aura_hub.referral.model.ReferralStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, Integer> {

    boolean existsByRefereeUserId(UUID refereeUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Referral r where r.refereeUserId = :refereeId")
    Optional<Referral> findByRefereeForUpdate(@Param("refereeId") UUID refereeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Referral r where r.orderId = :orderId")
    Optional<Referral> findByOrderIdForUpdate(@Param("orderId") Integer orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Referral r where r.id = :id")
    Optional<Referral> findByIdForUpdate(@Param("id") Integer id);

    List<Referral> findByReferrerUserIdOrderByCreatedAtDescIdDesc(UUID referrerUserId);

    /** Qualified referrals whose payment is past the hold, oldest first. */
    @Query("""
            select r.id from Referral r
            where r.status = com.course_aura_hub.referral.model.ReferralStatus.QUALIFIED and r.qualifiedAt <= :before
            order by r.qualifiedAt
            """)
    List<Integer> findDueIds(@Param("before") Instant before);

    Page<Referral> findByStatus(ReferralStatus status, Pageable pageable);
}
