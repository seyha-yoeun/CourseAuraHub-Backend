package com.course_aura_hub.auth.repository;

import com.course_aura_hub.auth.model.RefreshSession;
import com.course_aura_hub.auth.model.SessionRevocationReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash = :tokenHash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("""
            select count(s) > 0 from RefreshSession s
            where s.familyId = :familyId and s.revokedAt is null and s.expiresAt > :now
            """)
    boolean isFamilyActive(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Query("""
            select s from RefreshSession s
            where s.user.id = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.sessionStartedAt desc
            """)
    List<RefreshSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    boolean existsByFamilyIdAndUserId(UUID familyId, UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.familyId = :familyId and s.revokedAt is null
            """)
    int revokeFamily(
            @Param("familyId") UUID familyId,
            @Param("reason") SessionRevocationReason reason,
            @Param("now") Instant now
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshSession s set s.revokedAt = :now, s.revokedReason = :reason
            where s.user.id = :userId and s.revokedAt is null
            """)
    int revokeAllForUser(
            @Param("userId") UUID userId,
            @Param("reason") SessionRevocationReason reason,
            @Param("now") Instant now
    );

    @Modifying
    @Query("delete from RefreshSession s where s.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
