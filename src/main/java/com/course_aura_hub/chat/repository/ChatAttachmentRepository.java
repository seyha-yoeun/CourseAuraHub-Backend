package com.course_aura_hub.chat.repository;

import com.course_aura_hub.chat.model.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Integer> {

    List<ChatAttachment> findByMessageIdInOrderByIdAsc(Collection<Integer> messageIds);

    List<ChatAttachment> findByMessageId(Integer messageId);

    boolean existsByFileRef(String fileRef);
}
