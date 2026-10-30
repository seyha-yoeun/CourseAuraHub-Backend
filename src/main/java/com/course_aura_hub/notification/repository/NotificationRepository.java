package com.course_aura_hub.notification.repository;

import com.course_aura_hub.notification.model.Notification;
import com.course_aura_hub.notification.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNull(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    /** What a reconnecting stream missed, oldest first. */
    List<Notification> findTop50ByUserIdAndIdGreaterThanOrderByIdAsc(UUID userId, Long id);

    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);

    @Query("""
            select distinct n.userId
            from Notification n
            where n.userId in :userIds
              and n.type = :type
              and n.link = :link
              and n.readAt is null
            """)
    Set<UUID> findUserIdsWithUnread(
            @Param("userIds") Collection<UUID> userIds,
            @Param("type") NotificationType type,
            @Param("link") String link
    );
}
