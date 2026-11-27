package com.course_aura_hub.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone's membership and read state in a conversation. A row whose {@code leftAt} is set is a former group
 * member: kept so history and re-joining work, but it gives no access.
 */
@Entity
@DynamicUpdate
@Table(name = "chat_participants")
@IdClass(ChatParticipantId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipant {

    @Id
    @Column(name = "conversation_id", nullable = false, updatable = false)
    private Integer conversationId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, columnDefinition = "text")
    private ParticipantRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "left_reason", columnDefinition = "text")
    private LeftReason leftReason;

    @Column(name = "removed_by")
    private UUID removedBy;

    /** Highest message id this person has read; only ever moves forward. */
    @Column(name = "last_read_message_id", nullable = false)
    private int lastReadMessageId;

    /** Highest message id that reached one of this person's devices; only ever moves forward. */
    @Column(name = "last_delivered_message_id", nullable = false)
    private int lastDeliveredMessageId;

    @Column(name = "muted", nullable = false)
    private boolean muted;

    /** Hidden from the conversation list until something newer arrives. */
    @Column(name = "hidden_at")
    private Instant hiddenAt;

    /** Kept at the top of this person's conversation list; null when not pinned */
    @Column(name = "pinned_at")
    private Instant pinnedAt;

    public boolean isActive() {
        return leftAt == null;
    }

    @PrePersist
    void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }
}
