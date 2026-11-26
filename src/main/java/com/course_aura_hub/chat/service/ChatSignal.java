package com.course_aura_hub.chat.service;

import java.util.UUID;

/**
 * Something that happened in chat, to push to connected clients after the transaction commits. Carries ids only:
 * each instance reloads what it needs and decides per connected user who may see it, so the same signal can be
 * relayed to other instances over Postgres NOTIFY.
 *
 * @param conversationId the conversation it happened in
 * @param refId          the message or invite it is about, if any
 * @param actorId        who did it, if anyone
 * @param targetId       the one person it is for or about (a removed member, an invitee), if any
 * @param value          a cursor position for read and delivered signals
 */
public record ChatSignal(Kind kind, Integer conversationId, Integer refId, UUID actorId, UUID targetId, Integer value) {

    public enum Kind {
        MESSAGE_CREATED("message.created"),
        MESSAGE_UPDATED("message.updated"),
        READ("read"),
        DELIVERED("delivered"),
        TYPING("typing"),
        CONVERSATION_CREATED("conversation.created"),
        CONVERSATION_UPDATED("conversation.updated"),
        MEMBER_JOINED("member.joined"),
        MEMBER_LEFT("member.left"),
        MEMBER_ROLE("member.role"),
        INVITE_RECEIVED("invite.received"),
        INVITE_REVOKED("invite.revoked"),
        ACCESS_LOST("access.lost");

        private final String eventType;

        Kind(String eventType) {
            this.eventType = eventType;
        }

        public String eventType() {
            return eventType;
        }
    }

    public static ChatSignal message(Kind kind, Integer conversationId, Integer messageId, UUID actorId) {
        return new ChatSignal(kind, conversationId, messageId, actorId, null, null);
    }

    public static ChatSignal cursor(Kind kind, Integer conversationId, UUID actorId, int messageId) {
        return new ChatSignal(kind, conversationId, null, actorId, null, messageId);
    }

    public static ChatSignal conversation(Kind kind, Integer conversationId, UUID actorId, UUID targetId) {
        return new ChatSignal(kind, conversationId, null, actorId, targetId, null);
    }

    public static ChatSignal invite(Kind kind, Integer conversationId, Integer inviteId, UUID inviteeId) {
        return new ChatSignal(kind, conversationId, inviteId, null, inviteeId, null);
    }

    /** {@code KIND,conversationId,refId,actorId,targetId,value}, with empty fields for nulls. */
    public String encode() {
        return String.join(",", kind.name(), text(conversationId), text(refId), text(actorId), text(targetId), text(value));
    }

    /** The signal in an encoded body, or null when the body is malformed. */
    public static ChatSignal decode(String body) {
        if (body == null) {
            return null;
        }
        String[] parts = body.split(",", -1);
        if (parts.length != 6) {
            return null;
        }
        try {
            return new ChatSignal(Kind.valueOf(parts[0]), integer(parts[1]), integer(parts[2]), uuid(parts[3]),
                    uuid(parts[4]), integer(parts[5]));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Integer integer(String value) {
        return value.isEmpty() ? null : Integer.valueOf(value);
    }

    private static UUID uuid(String value) {
        return value.isEmpty() ? null : UUID.fromString(value);
    }
}
