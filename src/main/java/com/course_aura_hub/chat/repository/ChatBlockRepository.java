package com.course_aura_hub.chat.repository;

import com.course_aura_hub.chat.model.ChatBlock;
import com.course_aura_hub.chat.model.ChatBlockId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatBlockRepository extends JpaRepository<ChatBlock, ChatBlockId> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<ChatBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    /** Either person blocked the other. */
    @Query("""
            select count(b) > 0 from ChatBlock b
            where (b.blockerId = :a and b.blockedId = :b) or (b.blockerId = :b and b.blockedId = :a)
            """)
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);
}
