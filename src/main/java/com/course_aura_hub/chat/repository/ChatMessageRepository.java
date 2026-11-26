package com.course_aura_hub.chat.repository;

import com.course_aura_hub.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {

    Optional<ChatMessage> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    List<ChatMessage> findByConversationIdOrderByIdDesc(Integer conversationId, Pageable pageable);

    List<ChatMessage> findByConversationIdAndIdLessThanOrderByIdDesc(Integer conversationId, Integer id, Pageable pageable);

    List<ChatMessage> findByConversationIdAndIdGreaterThanOrderByIdAsc(Integer conversationId, Integer id, Pageable pageable);
}
