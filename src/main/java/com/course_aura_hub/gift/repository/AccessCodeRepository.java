package com.course_aura_hub.gift.repository;

import com.course_aura_hub.gift.model.AccessCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccessCodeRepository extends JpaRepository<AccessCode, Integer> {

    /** Locked so parallel redemptions can't exceed the code's uses. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AccessCode c join fetch c.course where c.codeHash = :hash")
    Optional<AccessCode> findByHashForUpdate(@Param("hash") String hash);

    Optional<AccessCode> findByGiftOrderId(Integer giftOrderId);

    @EntityGraph(attributePaths = "course")
    List<AccessCode> findByGiftOrderIdIsNullOrderByCreatedAtDescIdDesc();

    @EntityGraph(attributePaths = "course")
    List<AccessCode> findByBatchOrderByIdAsc(String batch);

    @EntityGraph(attributePaths = "course")
    List<AccessCode> findByPurchaserStudentIdOrderByCreatedAtDescIdDesc(Integer studentId);
}
