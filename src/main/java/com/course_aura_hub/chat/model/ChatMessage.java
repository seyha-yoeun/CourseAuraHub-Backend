package com.course_aura_hub.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import java.time.Instant;
import java.util.UUID;

/** One message in a conversation. Ids only grow, so they order the history. */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private Integer conversationId;

    /** Null for system lines, and for messages of a deleted account. */
    @Column(name = "sender_id")
    private UUID senderId;

    @Column(name = "client_message_id", updatable = false)
    private UUID clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, columnDefinition = "text")
    private MessageKind kind;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    /** The database clock, like every other chat timestamp it is compared with. */
    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "hidden_by")
    private UUID hiddenBy;
}
