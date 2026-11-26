package com.course_aura_hub.chat.repository;

import com.course_aura_hub.chat.model.ChatParticipant;
import com.course_aura_hub.chat.model.ChatParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, ChatParticipantId> {

    Optional<ChatParticipant> findByConversationIdAndUserId(Integer conversationId, UUID userId);

    List<ChatParticipant> findByConversationId(Integer conversationId);

    List<ChatParticipant> findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(Integer conversationId);

    long countByConversationIdAndLeftAtIsNull(Integer conversationId);

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            update chat_participants
            set last_read_message_id = greatest(last_read_message_id, :messageId),
                last_delivered_message_id = greatest(last_delivered_message_id, :messageId)
            where conversation_id = :conversationId and user_id = :userId
              and last_read_message_id < :messageId
            """)
    int advanceRead(@Param("conversationId") Integer conversationId, @Param("userId") UUID userId,
                    @Param("messageId") int messageId);

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            update chat_participants
            set last_delivered_message_id = greatest(last_delivered_message_id, :messageId)
            where conversation_id = :conversationId and user_id = :userId
              and last_delivered_message_id < :messageId
            """)
    int advanceDelivered(@Param("conversationId") Integer conversationId, @Param("userId") UUID userId,
                         @Param("messageId") int messageId);
}
