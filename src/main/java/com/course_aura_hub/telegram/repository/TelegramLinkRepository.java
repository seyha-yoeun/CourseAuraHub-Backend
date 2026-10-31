package com.course_aura_hub.telegram.repository;

import com.course_aura_hub.telegram.model.TelegramLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelegramLinkRepository extends JpaRepository<TelegramLink, UUID> {

    Optional<TelegramLink> findByChatId(Long chatId);

    List<TelegramLink> findByUserIdInAndEnabledTrue(Collection<UUID> userIds);

    @Modifying
    @Query("delete from TelegramLink link where link.chatId = :chatId")
    int deleteByChatId(@Param("chatId") Long chatId);
}
