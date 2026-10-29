package com.course_aura_hub.auth.repository;

import com.course_aura_hub.auth.model.OneTimeToken;
import com.course_aura_hub.auth.model.TokenPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from OneTimeToken t
            where t.user.id = :userId and t.purpose = :purpose
              and t.consumedAt is null and t.revokedAt is null
            """)
    Optional<OneTimeToken> findActiveForUpdate(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeToken t where t.purpose = :purpose and t.tokenHash = :tokenHash")
    Optional<OneTimeToken> findByPurposeAndTokenHashForUpdate(
            @Param("purpose") TokenPurpose purpose,
            @Param("tokenHash") String tokenHash
    );

    @Query("select t.user.id from OneTimeToken t where t.purpose = :purpose and t.tokenHash = :tokenHash")
    Optional<UUID> findOwnerId(@Param("purpose") TokenPurpose purpose, @Param("tokenHash") String tokenHash);

    Optional<OneTimeToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, TokenPurpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, TokenPurpose purpose, Instant after);

    @Modifying
    @Query("delete from OneTimeToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
