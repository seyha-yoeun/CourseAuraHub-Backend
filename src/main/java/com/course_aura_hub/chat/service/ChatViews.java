package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.model.ChatAttachment;
import com.course_aura_hub.chat.model.ChatConversation;
import com.course_aura_hub.chat.model.ChatMessage;
import com.course_aura_hub.chat.model.ChatParticipant;
import com.course_aura_hub.chat.model.ConversationType;
import com.course_aura_hub.chat.model.MessageKind;
import com.course_aura_hub.chat.repository.ChatAttachmentRepository;
import com.course_aura_hub.chat.repository.ChatMessageRepository;
import com.course_aura_hub.chat.repository.ChatParticipantRepository;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds chat responses for one viewer: names, signed attachment links, unread counts. */
@Component
@RequiredArgsConstructor
public class ChatViews {

    private static final int PREVIEW_LENGTH = 120;

    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatPeople people;
    private final FileService fileService;
    private final NamedParameterJdbcTemplate jdbc;

    public ChatDtos.Conversation conversation(UUID viewerId, boolean viewerIsAdmin, ChatAccess.View view) {
        ChatConversation conversation = view.conversation();
        ChatParticipant participant = view.participant();
        Set<UUID> ids = new HashSet<>();
        if (view.peerId() != null) {
            ids.add(view.peerId());
        }
        if (conversation.getType() == ConversationType.SUPPORT && conversation.getCreatedBy() != null) {
            ids.add(conversation.getCreatedBy());
        }
        ChatMessage last = conversation.getLastMessageId() == null
                ? null : messageRepository.findById(conversation.getLastMessageId()).orElse(null);
        if (last != null && last.getSenderId() != null) {
            ids.add(last.getSenderId());
        }
        Map<UUID, ChatDtos.Person> named = people.lookup(viewerId, ids);
        ChatDtos.Person peer = view.peerId() == null ? null : named.get(view.peerId());

        ChatDtos.Group group = null;
        if (conversation.getType() == ConversationType.GROUP) {
            group = new ChatDtos.Group(conversation.getDescription(), conversation.isAllowMemberInvites(),
                    participantRepository.countByConversationIdAndLeftAtIsNull(conversation.getId()),
                    conversation.getVersion(), conversation.getOwnerId());
        }
        ChatDtos.Receipt receipt = null;
        if (conversation.getType() == ConversationType.DIRECT && view.peerId() != null) {
            receipt = participantRepository.findByConversationIdAndUserId(conversation.getId(), view.peerId())
                    .map(row -> new ChatDtos.Receipt(row.getLastDeliveredMessageId(), row.getLastReadMessageId()))
                    .orElse(null);
        }
        Course course = view.course();
        return new ChatDtos.Conversation(
                conversation.getId(),
                lower(conversation.getType()),
                lower(conversation.getStatus()),
                title(conversation, course, peer, named, viewerId, viewerIsAdmin),
                course == null ? null : new ChatDtos.CourseRef(course.getId(), course.getSlug(), course.getTitle()),
                peer,
                last == null ? null : lastMessage(last, named),
                participant == null || !participant.isActive() ? 0 : unread(conversation.getId(), viewerId, participant.getLastReadMessageId()),
                participant != null && participant.isMuted(),
                view.canSend(),
                view.readOnlyReason(),
                participant == null || !participant.isActive() ? null : lower(participant.getRole()),
                participant == null ? 0 : participant.getLastReadMessageId(),
                group,
                receipt,
                conversation.getLastActivityAt());
    }

    public List<ChatDtos.Message> messages(UUID viewerId, List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<UUID, ChatDtos.Person> named = people.lookup(viewerId,
                messages.stream().map(ChatMessage::getSenderId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        Map<Integer, List<ChatAttachment>> files = attachmentRepository
                .findByMessageIdInOrderByIdAsc(messages.stream().map(ChatMessage::getId).toList()).stream()
                .collect(Collectors.groupingBy(ChatAttachment::getMessageId));
        List<ChatDtos.Message> views = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            views.add(message(viewerId, message, named, files.getOrDefault(message.getId(), List.of())));
        }
        return views;
    }

    public ChatDtos.Message message(UUID viewerId, ChatMessage message) {
        return messages(viewerId, List.of(message)).getFirst();
    }

    /** Unread messages others wrote after this cursor. */
    public long unread(Integer conversationId, UUID userId, int lastReadMessageId) {
        Long count = jdbc.queryForObject("""
                select count(*) from chat_messages m
                where m.conversation_id = :conversationId and m.id > :cursor and m.kind = 'TEXT'
                  and m.deleted_at is null and m.hidden_at is null and m.sender_id is distinct from :userId
                """, new MapSqlParameterSource("conversationId", conversationId)
                .addValue("cursor", lastReadMessageId).addValue("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    /** Unread messages across every conversation the user can still read, leaving out muted ones. */
    public long totalUnread(UUID userId) {
        Long count = jdbc.queryForObject("""
                select count(*)
                from chat_participants p
                join chat_conversations cv on cv.id = p.conversation_id
                join chat_messages m on m.conversation_id = p.conversation_id and m.id > p.last_read_message_id
                where p.user_id = :userId and p.left_at is null and not p.muted
                  and m.kind = 'TEXT' and m.deleted_at is null and m.hidden_at is null
                  and m.sender_id is distinct from :userId
                  and (cv.type not in ('COURSE', 'GROUP')
                       or exists (select 1 from users u, courses c where u.id = :userId and c.id = cv.course_id and %s))
                """.formatted(ChatEligibility.ELIGIBLE_SQL), new MapSqlParameterSource("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    public ChatDtos.LastMessage lastMessage(ChatMessage message, Map<UUID, ChatDtos.Person> named) {
        return new ChatDtos.LastMessage(message.getId(), preview(message, attachmentCount(message)),
                message.getSenderId() == null ? null : named.get(message.getSenderId()), message.getCreatedAt());
    }

    /** Short text for lists and notifications. */
    public static String preview(ChatMessage message, int attachments) {
        if (message.getDeletedAt() != null) {
            return "Message deleted";
        }
        if (message.getHiddenAt() != null) {
            return "Message hidden";
        }
        String body = message.getBody();
        if (body == null || body.isBlank()) {
            return attachments > 1 ? "Sent " + attachments + " files" : "Sent a file";
        }
        String flat = body.strip().replaceAll("\\s+", " ");
        return flat.length() <= PREVIEW_LENGTH ? flat : flat.substring(0, PREVIEW_LENGTH - 1) + "…";
    }

    private int attachmentCount(ChatMessage message) {
        return message.getKind() == MessageKind.SYSTEM || message.getBody() != null && !message.getBody().isBlank()
                ? 0 : attachmentRepository.findByMessageId(message.getId()).size();
    }

    private ChatDtos.Message message(UUID viewerId, ChatMessage message, Map<UUID, ChatDtos.Person> named,
                                     List<ChatAttachment> files) {
        boolean deleted = message.getDeletedAt() != null;
        boolean hidden = message.getHiddenAt() != null;
        boolean visible = !deleted && !hidden;
        boolean mine = viewerId != null && viewerId.equals(message.getSenderId());
        List<ChatDtos.Attachment> attachments = visible
                ? files.stream().map(file -> new ChatDtos.Attachment(file.getId(), file.getFileName(), file.getContentType(),
                file.getSizeBytes(), fileService.playableUrl(file.getFileRef()))).toList()
                : List.of();
        ChatDtos.Person sender = message.getSenderId() == null ? null : named.get(message.getSenderId());
        if (sender == null && message.getKind() == MessageKind.TEXT) {
            sender = new ChatDtos.Person(null, ChatPeople.DELETED_NAME, null, false);
        }
        return new ChatDtos.Message(message.getId(), message.getConversationId(), lower(message.getKind()), sender,
                visible ? message.getBody() : null, deleted, hidden, attachments,
                mine ? message.getClientMessageId() : null, mine, message.getCreatedAt());
    }

    private String title(ChatConversation conversation, Course course, ChatDtos.Person peer,
                         Map<UUID, ChatDtos.Person> named, UUID viewerId, boolean viewerIsAdmin) {
        return switch (conversation.getType()) {
            case DIRECT -> peer == null ? ChatPeople.DELETED_NAME : peer.name();
            case COURSE -> course == null ? "Course chat" : course.getTitle();
            case GROUP -> conversation.getTitle();
            case SUPPORT -> {
                ChatDtos.Person requester = named.get(conversation.getCreatedBy());
                yield viewerIsAdmin && requester != null && !requester.id().equals(viewerId)
                        ? "Support: " + requester.name() : "Support";
            }
        };
    }

    static String lower(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
