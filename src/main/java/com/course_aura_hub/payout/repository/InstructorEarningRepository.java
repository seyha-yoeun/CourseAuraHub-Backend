package com.course_aura_hub.payout.repository;

import com.course_aura_hub.payout.model.EarningStatus;
import com.course_aura_hub.payout.model.EarningType;
import com.course_aura_hub.payout.model.InstructorEarning;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstructorEarningRepository extends JpaRepository<InstructorEarning, Integer> {

    boolean existsByOrderIdAndType(Integer orderId, EarningType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from InstructorEarning e where e.order.id = :orderId and e.type = :type")
    Optional<InstructorEarning> findByOrderIdAndTypeForUpdate(@Param("orderId") Integer orderId,
                                                              @Param("type") EarningType type);

    /** The rows a new payout claims. Locked, so a parallel request waits and then finds them taken. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from InstructorEarning e
            where e.instructorId = :instructorId and e.status = com.course_aura_hub.payout.model.EarningStatus.AVAILABLE
              and e.payoutId is null
            order by e.id
            """)
    List<InstructorEarning> findClaimableForUpdate(@Param("instructorId") UUID instructorId);

    List<InstructorEarning> findByPayoutId(Integer payoutId);

    Page<InstructorEarning> findByInstructorId(UUID instructorId, Pageable pageable);

    Page<InstructorEarning> findByInstructorIdAndStatus(UUID instructorId, EarningStatus status, Pageable pageable);

    @Query("""
            select coalesce(sum(e.net), 0) from InstructorEarning e
            where e.instructorId = :instructorId and e.status = :status
            """)
    BigDecimal sumNet(@Param("instructorId") UUID instructorId, @Param("status") EarningStatus status);

    /** Available money that no payout has claimed yet. */
    @Query("""
            select coalesce(sum(e.net), 0) from InstructorEarning e
            where e.instructorId = :instructorId and e.status = com.course_aura_hub.payout.model.EarningStatus.AVAILABLE
              and e.payoutId is null
            """)
    BigDecimal sumUnclaimed(@Param("instructorId") UUID instructorId);

    /** Available money claimed by a payout that hasn't been paid yet. */
    @Query("""
            select coalesce(sum(e.net), 0) from InstructorEarning e
            where e.instructorId = :instructorId and e.status = com.course_aura_hub.payout.model.EarningStatus.AVAILABLE
              and e.payoutId is not null
            """)
    BigDecimal sumClaimed(@Param("instructorId") UUID instructorId);

    @Modifying
    @Query("""
            update InstructorEarning e set e.status = com.course_aura_hub.payout.model.EarningStatus.AVAILABLE
            where e.status = com.course_aura_hub.payout.model.EarningStatus.PENDING and e.availableAt <= :now
            """)
    int releaseDue(@Param("now") Instant now);
}
