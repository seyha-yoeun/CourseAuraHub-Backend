package com.course_aura_hub.chat.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chat request and response bodies. People are named by {@link Person}: learners see other learners by a short
 * name ("Jane L."), staff are shown in full, and emails appear only in admin-only responses.
 */
public final class ChatDtos {

    public static final int MAX_BODY_LENGTH = 4000;

    private ChatDtos() {
    }

    // ---- Requests ----

    public record DirectRequest(@NotNull(message = "Choose who to message") UUID userId) {
    }

    /**
     * @param clientMessageId the sender's own id for this message; sending again with the same id returns the
     *                        first message instead of storing a copy
     */
    public record SendMessageRequest(
            @NotNull(message = "clientMessageId is required") UUID clientMessageId,
            @Size(max = MAX_BODY_LENGTH, message = "Messages can be at most 4000 characters") String body,
            @Size(max = 5, message = "Attach at most 5 files") List<UUID> attachmentIds,
            // An earlier message in the same conversation this one replies to
            Integer replyToMessageId
    ) {

        public SendMessageRequest(UUID clientMessageId, String body, List<UUID> attachmentIds) {
            this(clientMessageId, body, attachmentIds, null);
        }
    }

    public record ReadRequest(@NotNull(message = "messageId is required") @Positive Integer messageId) {
    }

    /** The new text; may be empty only when the message has files. */
    public record EditMessageRequest(
            @Size(max = MAX_BODY_LENGTH, message = "Messages can be at most 4000 characters") String body
    ) {
    }

    /** @param emoji one of the offered reactions: 👍 ❤️ 😂 😮 😢 🎉 */
    public record ReactRequest(
            @NotBlank(message = "Choose a reaction") @Size(max = 16, message = "Choose one of the offered reactions") String emoji
    ) {
    }

    /** Only the fields that are set change. */
    public record ConversationUpdateRequest(Boolean muted, Boolean hidden) {
    }

    /** @param showPresence whether direct contacts see when I'm active; unchanged when null */
    public record SettingsRequest(
            @NotBlank(message = "Choose who may message you") String directMessages,
            Boolean showPresence
    ) {
    }

    public record CreateGroupRequest(
            @NotBlank(message = "Give the group a name") @Size(max = 80, message = "Group names can be at most 80 characters") String title,
            @Size(max = 500, message = "Descriptions can be at most 500 characters") String description,
            Boolean allowMemberInvites,
            @Size(max = 20, message = "Invite at most 20 people at a time") List<UUID> inviteUserIds
    ) {
    }

    /** Only the fields that are set change; {@code version} must match the group's current version. */
    public record UpdateGroupRequest(
            @Size(min = 1, max = 80, message = "Group names can be 1 to 80 characters") String title,
            @Size(max = 500, message = "Descriptions can be at most 500 characters") String description,
            Boolean allowMemberInvites,
            @NotNull(message = "version is required") Integer version
    ) {
    }

    public record InviteRequest(
            @NotEmpty(message = "Choose who to invite") @Size(max = 20, message = "Invite at most 20 people at a time") List<UUID> userIds
    ) {
    }

    public record RoleRequest(@NotBlank(message = "Choose a role") String role) {
    }

    public record TransferRequest(@NotNull(message = "Choose the new owner") UUID userId) {
    }

    public record RestrictionRequest(
            @NotNull(message = "Choose when the restriction ends") @Future(message = "The end must be in the future") Instant until,
            @NotBlank(message = "Give a reason") @Size(max = 500, message = "Reasons can be at most 500 characters") String reason
    ) {
    }

    // ---- WebSocket commands (/app/chat.*) ----

    public record SocketSend(Integer conversationId, UUID clientMessageId, String body, List<UUID> attachmentIds,
                             Integer replyToMessageId) {
    }

    public record SocketCursor(Integer conversationId, Integer messageId) {
    }

    public record SocketTyping(Integer conversationId) {
    }

    // ---- Responses ----

    /** @param staff an instructor or admin, shown by full name */
    public record Person(UUID id, String name, String avatarUrl, boolean staff) {
    }

    /** @param url a short-lived signed link; null when file storage is off */
    public record Attachment(Integer id, String fileName, String contentType, long size, String url) {
    }

    /**
     * The message a reply quotes.
     *
     * @param preview a short text, or "Message deleted" / "Message hidden" once it is gone
     */
    public record ReplyRef(Integer id, Person sender, String preview, boolean unavailable) {
    }

    /**
     * Everyone who reacted to a message with one emoji.
     *
     * @param mine   whether the viewer is one of them
     * @param people the most recent few of them (at most 3, newest first), for a "Jane L., Bo K. and 4 others" label
     */
    public record Reaction(String emoji, long count, boolean mine, List<Person> people) {
    }

    /**
     * @param sender          null for system lines and for messages of a deleted account
     * @param replyTo         the quoted message, when this one is a reply
     * @param reactions       by emoji, in the order they were first used; empty once the message is deleted or hidden
     * @param body            null once the message is deleted or hidden
     * @param clientMessageId only on your own messages
     * @param editedAt        when the sender last edited it, or null
     */
    public record Message(
            Integer id,
            Integer conversationId,
            String kind,
            Person sender,
            String body,
            boolean deleted,
            boolean hidden,
            List<Attachment> attachments,
            ReplyRef replyTo,
            List<Reaction> reactions,
            UUID clientMessageId,
            boolean mine,
            Instant createdAt,
            Instant editedAt
    ) {
    }

    public record MessagePage(List<Message> items, boolean hasMore) {
    }

    public record CourseRef(Integer id, String slug, String title) {
    }

    public record LastMessage(Integer id, String preview, Person sender, Instant createdAt) {
    }

    public record Group(String description, boolean allowMemberInvites, long memberCount, int version, UUID ownerId) {
    }

    /**
     * Whether someone is in chat right now ("Active now"), or when they last were ("Active 5m ago").
     *
     * @param lastSeenAt null when they have never been online in chat
     */
    public record Presence(boolean online, Instant lastSeenAt) {
    }

    /** How far the other person in a direct conversation has got: "Delivered" and "Seen" ticks. */
    public record Receipt(int deliveredMessageId, int readMessageId) {
    }

    /**
     * @param peer           the other person, for direct conversations
     * @param hidden         I hid it from my list and no message has arrived since
     * @param readOnlyReason why you can't send right now, or null when you can
     * @param myRole         owner, admin or member in a group; member elsewhere; null without a membership
     * @param peerReceipt    direct conversations only
     * @param peerPresence   direct conversations only; null when the peer hides it or either of you blocked the other
     */
    public record Conversation(
            Integer id,
            String type,
            String status,
            String title,
            CourseRef course,
            Person peer,
            LastMessage lastMessage,
            long unread,
            boolean muted,
            boolean hidden,
            boolean canSend,
            String readOnlyReason,
            String myRole,
            int lastReadMessageId,
            Group group,
            Receipt peerReceipt,
            Presence peerPresence,
            Instant lastActivityAt
    ) {
    }

    public record ConversationPage(List<Conversation> items, String nextCursor) {
    }

    public record UnreadCount(long total) {
    }

    /** @param url the WebSocket URL path to connect to with the ticket as the {@code ticket} query parameter */
    public record SocketTicket(String ticket, String url, Instant expiresAt) {
    }

    /** @param showPresence whether direct contacts see when I'm active */
    public record Settings(String directMessages, boolean showPresence) {
    }

    public record BlockedUser(Person user, Instant blockedAt) {
    }

    public record MemberPage(List<Person> items, String nextCursor) {
    }

    public record GroupMember(Person user, String role, Instant joinedAt) {
    }

    public record Invite(
            Integer id,
            Integer conversationId,
            String groupTitle,
            CourseRef course,
            Person inviter,
            Person invitee,
            String status,
            Instant createdAt,
            Instant expiresAt
    ) {
    }

    /** @param status invited, already_invited, already_member, unavailable or group_full */
    public record InviteResult(UUID userId, String status, Integer inviteId) {
    }

    public record InviteResults(List<InviteResult> results) {
    }

    public record GroupCreated(Conversation conversation, List<InviteResult> invites) {
    }

    // ---- Admin-only responses (may include emails) ----

    public record SupportRequester(UUID id, String name, String email) {
    }

    public record SupportThread(
            Integer id,
            String status,
            SupportRequester requester,
            LastMessage lastMessage,
            Instant createdAt,
            Instant lastActivityAt
    ) {
    }

    public record SupportPage(List<SupportThread> items, int page, int size, long total) {
    }

    /**
     * A reported message with the ten messages before and after it, for the admin deciding on the report.
     *
     * @param edits earlier texts of those messages, oldest first, so an edit can't hide what was reported
     */
    public record ReportContext(
            Integer reportId,
            Integer conversationId,
            String conversationType,
            CourseRef course,
            Integer messageId,
            List<Message> messages,
            List<MessageEdit> edits
    ) {
    }

    /** @param body the text before this edit; null when the message had only files */
    public record MessageEdit(Integer messageId, String body, Instant editedAt) {
    }

    public record Restriction(UUID userId, Instant until, String reason, Instant createdAt) {
    }

    // ---- WebSocket events on /user/queue/chat ----

    /** @param type for example message.created, read, typing, access.lost */
    public record Event(String type, Object data) {
    }
}
