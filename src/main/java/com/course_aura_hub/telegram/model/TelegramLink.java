package com.course_aura_hub.telegram.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_links")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramLink {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "chat_id", nullable = false, unique = true)
    private Long chatId;

    @Column(name = "telegram_name", columnDefinition = "text")
    private String telegramName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;
}
