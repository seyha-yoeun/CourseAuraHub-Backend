package com.course_aura_hub.chat.repository;

import com.course_aura_hub.chat.model.ChatConversation;
import com.course_aura_hub.chat.model.ConversationStatus;
import com.course_aura_hub.chat.model.ConversationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Integer> {

    /** Serializes membership changes of one conversation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id = :id")
    Optional<ChatConversation> findByIdForUpdate(@Param("id") Integer id);

    Optional<ChatConversation> findByTypeAndCourseId(ConversationType type, Integer courseId);

    Optional<ChatConversation> findByTypeAndDirectKey(ConversationType type, String directKey);

    Optional<ChatConversation> findFirstByTypeAndCreatedByAndStatus(ConversationType type, UUID createdBy, ConversationStatus status);

    List<ChatConversation> findByTypeAndCourseIdAndStatus(ConversationType type, Integer courseId, ConversationStatus status);

    /** Moves the last-message pointer forward only, so concurrent sends never move it back. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ChatConversation c set c.lastMessageId = :messageId, c.lastActivityAt = :at
            where c.id = :id and (c.lastMessageId is null or c.lastMessageId < :messageId)
            """)
    int advanceLastMessage(@Param("id") Integer id, @Param("messageId") Integer messageId, @Param("at") Instant at);
}
