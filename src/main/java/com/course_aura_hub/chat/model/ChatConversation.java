package com.course_aura_hub.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

/** A chat conversation of any type; see {@link ConversationType}. */
@Entity
@DynamicUpdate
@Table(name = "chat_conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, columnDefinition = "text")
    private ConversationType type;

    @Column(name = "course_id", updatable = false)
    private Integer courseId;

    @Column(name = "direct_key", updatable = false, columnDefinition = "text")
    private String directKey;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** The group's current owner, kept in step with the OWNER participant row. */
    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "allow_member_invites", nullable = false)
    private boolean allowMemberInvites;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private ConversationStatus status;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_id")
    private Integer lastMessageId;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    /** Bumped on every settings change; clients send it back so a stale edit is refused, not applied. */
    @Column(name = "version", nullable = false)
    private int version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastActivityAt == null) {
            lastActivityAt = now;
        }
    }
}
