package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.config.ChatProperties;
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

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    // People named on a reaction, for its "Jane L., Bo K. and 4 others" label
    private static final int REACTION_NAMES = 3;

    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatPeople people;
    private final ChatPresence presence;
    private final ChatAccess access;
    private final ChatProperties properties;
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
        // The newest message this viewer still sees (they may have deleted the last one for themselves)
        ChatMessage last = conversation.getLastMessageId() == null ? null
                : messageRepository.findLatestVisibleUpTo(conversation.getId(), conversation.getLastMessageId(), viewerId).orElse(null);
        if (last != null && last.getSenderId() != null) {
            ids.add(last.getSenderId());
        }
        List<PinRow> pinRows = pinRows(conversation.getId(), viewerId);
        Map<Integer, ChatMessage> pinned = pinRows.isEmpty() ? Map.of() : messageRepository
                .findAllById(pinRows.stream().map(PinRow::messageId).toList()).stream()
                .collect(Collectors.toMap(ChatMessage::getId, item -> item));
        pinRows.forEach(row -> ids.add(row.pinnedBy()));
        pinned.values().forEach(message -> ids.add(message.getSenderId()));
        ids.remove(null);
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
                isHidden(conversation, participant),
                participant != null && participant.isActive() && participant.getPinnedAt() != null,
                view.canSend(),
                view.readOnlyReason(),
                participant == null || !participant.isActive() ? null : lower(participant.getRole()),
                participant == null ? 0 : participant.getLastReadMessageId(),
                group,
                receipt,
                conversation.getType() == ConversationType.DIRECT ? presence.forViewer(viewerId, view.peerId()) : null,
                pinViews(pinRows, pinned, named),
                viewerId != null && access.canPin(view, viewerId),
                conversation.getLastActivityAt());
    }

    private record PinRow(Integer messageId, UUID pinnedBy, Instant pinnedAt) {
    }

    /** Pins this viewer can see: not of unsent or hidden messages, nor of ones they deleted for themselves. */
    private List<PinRow> pinRows(Integer conversationId, UUID viewerId) {
        return jdbc.query("""
                select p.message_id, p.pinned_by, p.pinned_at
                from chat_pins p join chat_messages m on m.id = p.message_id
                where p.conversation_id = :conversationId and m.deleted_at is null and m.hidden_at is null
                  and not exists (select 1 from chat_message_hidden_for h where h.message_id = p.message_id and h.user_id = :viewerId)
                order by p.pinned_at desc
                limit :limit
                """, new MapSqlParameterSource("conversationId", conversationId).addValue("viewerId", viewerId)
                .addValue("limit", properties.maxPins()), (row, index) -> new PinRow(row.getInt("message_id"),
                row.getObject("pinned_by", UUID.class), row.getTimestamp("pinned_at").toInstant()));
    }

    private List<ChatDtos.Pin> pinViews(List<PinRow> rows, Map<Integer, ChatMessage> messages, Map<UUID, ChatDtos.Person> named) {
        List<ChatDtos.Pin> pins = new ArrayList<>(rows.size());
        for (PinRow row : rows) {
            ChatMessage message = messages.get(row.messageId());
            if (message == null) {
                continue;
            }
            ChatDtos.Person sender = message.getSenderId() == null
                    ? new ChatDtos.Person(null, ChatPeople.DELETED_NAME, null, false) : named.get(message.getSenderId());
            pins.add(new ChatDtos.Pin(message.getId(), preview(message, attachmentCount(message)), sender,
                    row.pinnedBy() == null ? null : named.get(row.pinnedBy()), row.pinnedAt()));
        }
        return pins;
    }

    /** Hidden from the viewer's list: they hid it and nothing happened in it since. */
    static boolean isHidden(ChatConversation conversation, ChatParticipant participant) {
        return participant != null && participant.isActive() && participant.getHiddenAt() != null
                && !conversation.getLastActivityAt().isAfter(participant.getHiddenAt());
    }

    public List<ChatDtos.Message> messages(UUID viewerId, List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        // Quoted messages, loaded in one go for the whole page
        Set<Integer> replyIds = messages.stream().map(ChatMessage::getReplyToMessageId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, ChatMessage> quoted = replyIds.isEmpty() ? Map.of()
                : messageRepository.findAllById(replyIds).stream().collect(Collectors.toMap(ChatMessage::getId, item -> item));
        Map<Integer, Map<String, Tally>> reactions = reactions(viewerId, messages);
        Set<UUID> senders = new HashSet<>();
        messages.forEach(message -> senders.add(message.getSenderId()));
        quoted.values().forEach(message -> senders.add(message.getSenderId()));
        reactions.values().forEach(byEmoji -> byEmoji.values().forEach(tally -> senders.addAll(tally.recent)));
        senders.remove(null);
        Map<UUID, ChatDtos.Person> named = people.lookup(viewerId, senders);
        List<Integer> withFiles = new ArrayList<>(messages.stream().map(ChatMessage::getId).toList());
        withFiles.addAll(quoted.keySet());
        Map<Integer, List<ChatAttachment>> files = attachmentRepository.findByMessageIdInOrderByIdAsc(withFiles).stream()
                .collect(Collectors.groupingBy(ChatAttachment::getMessageId));
        List<ChatDtos.Message> views = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            ChatMessage target = message.getReplyToMessageId() == null ? null : quoted.get(message.getReplyToMessageId());
            ChatDtos.ReplyRef reply = target == null ? null : replyRef(target, named, files.getOrDefault(target.getId(), List.of()).size());
            views.add(message(viewerId, message, named, files.getOrDefault(message.getId(), List.of()), reply,
                    reactionViews(reactions.get(message.getId()), named)));
        }
        return views;
    }

    /** Earlier texts of these messages, oldest first: for moderators, never shown in conversations. */
    public List<ChatDtos.MessageEdit> editHistory(List<Integer> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                select message_id, body, edited_at from chat_message_edits where message_id in (:ids) order by id
                """, new MapSqlParameterSource("ids", messageIds), (row, index) -> new ChatDtos.MessageEdit(
                row.getInt("message_id"), row.getString("body"), row.getTimestamp("edited_at").toInstant()));
    }

    /** How many people reacted with one emoji, whether the viewer did, and the few who did most recently. */
    private static final class Tally {
        private long count;
        private boolean mine;
        private final Deque<UUID> recent = new ArrayDeque<>(REACTION_NAMES);
    }

    /** Reactions to the messages people can still see, by message and then emoji in the order first used. */
    private Map<Integer, Map<String, Tally>> reactions(UUID viewerId, List<ChatMessage> messages) {
        List<Integer> ids = messages.stream()
                .filter(message -> message.getKind() == MessageKind.TEXT && message.getDeletedAt() == null && message.getHiddenAt() == null)
                .map(ChatMessage::getId)
                .toList();
        Map<Integer, Map<String, Tally>> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.query("""
                select message_id, user_id, emoji from chat_message_reactions
                where message_id in (:ids) order by created_at, user_id
                """, new MapSqlParameterSource("ids", ids), row -> {
            UUID userId = row.getObject("user_id", UUID.class);
            Tally tally = out.computeIfAbsent(row.getInt("message_id"), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getString("emoji"), ignored -> new Tally());
            tally.count++;
            tally.mine |= userId.equals(viewerId);
            if (tally.recent.size() == REACTION_NAMES) {
                tally.recent.removeFirst();
            }
            tally.recent.addLast(userId);
        });
        return out;
    }

    private static List<ChatDtos.Reaction> reactionViews(Map<String, Tally> byEmoji, Map<UUID, ChatDtos.Person> named) {
        if (byEmoji == null) {
            return List.of();
        }
        List<ChatDtos.Reaction> views = new ArrayList<>(byEmoji.size());
        byEmoji.forEach((emoji, tally) -> {
            List<ChatDtos.Person> recent = new ArrayList<>();
            tally.recent.descendingIterator().forEachRemaining(userId -> {
                ChatDtos.Person person = named.get(userId);
                if (person != null) {
                    recent.add(person);
                }
            });
            views.add(new ChatDtos.Reaction(emoji, tally.count, tally.mine, recent));
        });
        return views;
    }

    private static ChatDtos.ReplyRef replyRef(ChatMessage target, Map<UUID, ChatDtos.Person> named, int attachments) {
        boolean unavailable = target.getDeletedAt() != null || target.getHiddenAt() != null;
        ChatDtos.Person sender = target.getSenderId() == null
                ? new ChatDtos.Person(null, ChatPeople.DELETED_NAME, null, false)
                : named.get(target.getSenderId());
        return new ChatDtos.ReplyRef(target.getId(), sender, preview(target, attachments), unavailable);
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
                  and not exists (select 1 from chat_message_hidden_for h where h.message_id = m.id and h.user_id = :userId)
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
                  and not exists (select 1 from chat_message_hidden_for h where h.message_id = m.id and h.user_id = :userId)
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
                                     List<ChatAttachment> files, ChatDtos.ReplyRef replyTo, List<ChatDtos.Reaction> reactions) {
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
                visible ? message.getBody() : null, deleted, hidden, attachments, replyTo, visible ? reactions : List.of(),
                mine ? message.getClientMessageId() : null, mine, message.getCreatedAt(), visible ? message.getEditedAt() : null);
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
