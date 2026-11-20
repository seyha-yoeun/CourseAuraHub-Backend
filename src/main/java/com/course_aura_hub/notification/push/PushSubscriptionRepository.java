package com.course_aura_hub.notification.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Integer> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUserIdIn(Collection<UUID> userIds);

    long countByUserId(UUID userId);

    @Modifying
    @Query("delete from PushSubscription p where p.userId = :userId and p.endpoint = :endpoint")
    int deleteByUserIdAndEndpoint(@Param("userId") UUID userId, @Param("endpoint") String endpoint);
}
