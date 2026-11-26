package com.course_aura_hub.chat.repository;

import com.course_aura_hub.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {

    Optional<ChatMessage> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    List<ChatMessage> findByConversationIdOrderByIdDesc(Integer conversationId, Pageable pageable);

    // ---- History as one person sees it: messages they deleted for themselves are left out ----

    String NOT_HIDDEN_FOR_VIEWER = " and not exists (select 1 from chat_message_hidden_for h where h.message_id = m.id and h.user_id = :viewer) ";

    @Query(nativeQuery = true, value = "select m.* from chat_messages m where m.conversation_id = :conversationId"
            + NOT_HIDDEN_FOR_VIEWER + "order by m.id desc limit :limit")
    List<ChatMessage> findLatestVisible(@Param("conversationId") Integer conversationId, @Param("viewer") UUID viewer,
                                        @Param("limit") int limit);

    @Query(nativeQuery = true, value = "select m.* from chat_messages m where m.conversation_id = :conversationId and m.id < :before"
            + NOT_HIDDEN_FOR_VIEWER + "order by m.id desc limit :limit")
    List<ChatMessage> findVisibleBefore(@Param("conversationId") Integer conversationId, @Param("before") Integer before,
                                        @Param("viewer") UUID viewer, @Param("limit") int limit);

    @Query(nativeQuery = true, value = "select m.* from chat_messages m where m.conversation_id = :conversationId and m.id > :after"
            + NOT_HIDDEN_FOR_VIEWER + "order by m.id asc limit :limit")
    List<ChatMessage> findVisibleAfter(@Param("conversationId") Integer conversationId, @Param("after") Integer after,
                                       @Param("viewer") UUID viewer, @Param("limit") int limit);

    /** The newest message up to an id that this person hasn't deleted for themselves, for the conversation list. */
    @Query(nativeQuery = true, value = "select m.* from chat_messages m where m.conversation_id = :conversationId and m.id <= :upTo"
            + NOT_HIDDEN_FOR_VIEWER + "order by m.id desc limit 1")
    Optional<ChatMessage> findLatestVisibleUpTo(@Param("conversationId") Integer conversationId, @Param("upTo") Integer upTo,
                                                @Param("viewer") UUID viewer);

    List<ChatMessage> findByConversationIdAndIdLessThanOrderByIdDesc(Integer conversationId, Integer id, Pageable pageable);

    List<ChatMessage> findByConversationIdAndIdGreaterThanOrderByIdAsc(Integer conversationId, Integer id, Pageable pageable);
}
