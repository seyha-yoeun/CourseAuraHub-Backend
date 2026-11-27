package com.course_aura_hub.chat.repository;

import com.course_aura_hub.chat.model.ChatGroupInvite;
import com.course_aura_hub.chat.model.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatGroupInviteRepository extends JpaRepository<ChatGroupInvite, Integer> {

    Optional<ChatGroupInvite> findByConversationIdAndInviteeIdAndStatus(Integer conversationId, UUID inviteeId, InviteStatus status);

    List<ChatGroupInvite> findByConversationIdAndStatus(Integer conversationId, InviteStatus status);

    List<ChatGroupInvite> findByInviteeIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(UUID inviteeId, InviteStatus status, Instant now);

    long countByInviterIdAndCreatedAtAfter(UUID inviterId, Instant since);

    @Modifying
    @Query("""
            update ChatGroupInvite i set i.status = com.course_aura_hub.chat.model.InviteStatus.EXPIRED, i.respondedAt = :now
            where i.status = com.course_aura_hub.chat.model.InviteStatus.PENDING and i.expiresAt <= :now
            """)
    int expireDue(@Param("now") Instant now);
}
