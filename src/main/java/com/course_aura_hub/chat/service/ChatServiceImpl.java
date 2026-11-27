package com.course_aura_hub.chat.service;

import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.chat.config.ChatProperties;
import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.model.ChatAttachment;
import com.course_aura_hub.chat.model.ChatBlock;
import com.course_aura_hub.chat.model.ChatConversation;
import com.course_aura_hub.chat.model.ChatMessage;
import com.course_aura_hub.chat.model.ChatParticipant;
import com.course_aura_hub.chat.model.ChatSettings;
import com.course_aura_hub.chat.model.ConversationStatus;
import com.course_aura_hub.chat.model.ConversationType;
import com.course_aura_hub.chat.model.DirectMessagePolicy;
import com.course_aura_hub.chat.model.MessageKind;
import com.course_aura_hub.chat.model.ParticipantRole;
import com.course_aura_hub.chat.repository.ChatAttachmentRepository;
import com.course_aura_hub.chat.repository.ChatBlockRepository;
import com.course_aura_hub.chat.repository.ChatConversationRepository;
import com.course_aura_hub.chat.repository.ChatMessageRepository;
import com.course_aura_hub.chat.repository.ChatParticipantRepository;
import com.course_aura_hub.chat.repository.ChatSettingsRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.file.dto.StoredFileResponse;
import com.course_aura_hub.file.model.FilePurpose;
import com.course_aura_hub.file.service.FileService;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    static final int MAX_PAGE = 50;
    static final String CANT_START_DIRECT = "You can't start a conversation with this person";
    private static final String TOO_FAST = "You're sending messages too quickly. Wait a moment and try again.";
    // Hidden from my list until a message arrives after I hid it (ChatViews.isHidden says the same per conversation)
    private static final String SHOWN_SQL = "(p.hidden_at is null or cv.last_activity_at > p.hidden_at)";
    private static final String HIDDEN_SQL = "(p.hidden_at is not null and cv.last_activity_at <= p.hidden_at)";
    private static final String TOO_MANY_REACTIONS = "You're reacting too quickly. Wait a moment and try again.";

    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final ChatBlockRepository blockRepository;
    private final ChatSettingsRepository settingsRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ChatAccess access;
    private final ChatEligibility eligibility;
    private final ChatPeople people;
    private final ChatViews views;
    private final ChatRateLimiter rateLimiter;
    private final ChatSocketTickets socketTickets;
    private final ChatGroupService groupService;
    private final ChatProperties properties;
    private final FileService fileService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public ChatDtos.SocketTicket issueSocketTicket(Actor actor) {
        return socketTickets.issue(actor.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public ChatDtos.ConversationPage conversations(Actor actor, String cursor, int limit, boolean hidden) {
        int size = clamp(limit, 20);
        MapSqlParameterSource params = new MapSqlParameterSource("me", actor.userId()).addValue("limit", size + 1);
        String after = "";
        if (StringUtils.hasText(cursor)) {
            String[] parts = decodeCursor(cursor);
            try {
                params.addValue("at", Timestamp.from(Instant.parse(parts[0]))).addValue("cid", Integer.valueOf(parts[1]));
            } catch (RuntimeException ex) {
                throw new BusinessRuleException("Invalid cursor");
            }
            after = "and (cv.last_activity_at, cv.id) < (:at, :cid)";
        }
        String listed = """
                select cv.id
                from chat_participants p
                join chat_conversations cv on cv.id = p.conversation_id
                where p.user_id = :me and p.left_at is null
                  and %s
                  and (cv.type not in ('COURSE', 'GROUP')
                       or exists (select 1 from users u, courses c where u.id = :me and c.id = cv.course_id and %s))
                  %s
                """;
        // The main list keeps pinned conversations out of its pages and puts them all first on page one
        String pinnedFilter = hidden ? "" : "and p.pinned_at is null";
        List<Integer> pinnedIds = hidden || StringUtils.hasText(cursor) ? List.of() : jdbc.queryForList(
                listed.formatted(SHOWN_SQL, ChatEligibility.ELIGIBLE_SQL, "and p.pinned_at is not null")
                        + " order by p.pinned_at desc", params, Integer.class);
        List<Integer> ids = jdbc.queryForList(
                listed.formatted(hidden ? HIDDEN_SQL : SHOWN_SQL, ChatEligibility.ELIGIBLE_SQL, pinnedFilter + " " + after)
                        + " order by cv.last_activity_at desc, cv.id desc limit :limit", params, Integer.class);
        boolean more = ids.size() > size;
        List<ChatDtos.Conversation> pinned = load(actor, pinnedIds);
        List<ChatDtos.Conversation> page = load(actor, more ? ids.subList(0, size) : ids);
        String next = null;
        if (more && !page.isEmpty()) {
            ChatDtos.Conversation last = page.getLast();
            next = encodeCursor(last.lastActivityAt().toString(), last.id().toString());
        }
        List<ChatDtos.Conversation> items = new ArrayList<>(pinned);
        items.addAll(page);
        return new ChatDtos.ConversationPage(items, next);
    }

    private List<ChatDtos.Conversation> load(Actor actor, List<Integer> ids) {
        List<ChatDtos.Conversation> items = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            conversationRepository.findById(id).ifPresent(conversation ->
                    items.add(views.conversation(actor.userId(), actor.admin(), access.requireReadable(actor, conversation))));
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public ChatDtos.UnreadCount unreadCount(Actor actor) {
        return new ChatDtos.UnreadCount(views.totalUnread(actor.userId()));
    }

    @Override
    @Transactional
    public ChatDtos.Conversation openDirect(Actor actor, UUID userId) {
        UUID me = actor.userId();
        if (me.equals(userId)) {
            throw new BusinessRuleException("You can't message yourself");
        }
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.of("User", userId);
        }
        String key = directKey(me, userId);
        ChatConversation existing = conversationRepository.findByTypeAndDirectKey(ConversationType.DIRECT, key).orElse(null);
        if (existing != null) {
            unhide(existing.getId(), me);
            return views.conversation(me, actor.admin(), access.requireReadable(actor, existing));
        }

        access.requireNotRestricted(me);
        if (rateLimiter.enabled() && countSince(
                "select count(*) from chat_conversations where type = 'DIRECT' and created_by = :me and created_at > :since",
                me, Duration.ofDays(1)) >= properties.directsPerDay()) {
            throw new BusinessRuleException("You've started a lot of new conversations today. Try again tomorrow.");
        }
        if (!mayStartDirect(me, userId)) {
            throw new ForbiddenActionException(CANT_START_DIRECT);
        }
        jdbc.update("""
                insert into chat_conversations (type, direct_key, status, created_by, allow_member_invites, version,
                                                created_at, last_activity_at)
                values ('DIRECT', :key, 'ACTIVE', :me, false, 0, now(), now())
                on conflict (direct_key) where type = 'DIRECT' do nothing
                """, new MapSqlParameterSource("key", key).addValue("me", me));
        ChatConversation conversation = conversationRepository.findByTypeAndDirectKey(ConversationType.DIRECT, key)
                .orElseThrow(() -> new IllegalStateException("Direct conversation vanished: " + key));
        int cursor = conversation.getLastMessageId() == null ? 0 : conversation.getLastMessageId();
        addParticipantIfAbsent(conversation.getId(), me, cursor);
        addParticipantIfAbsent(conversation.getId(), userId, cursor);
        events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.CONVERSATION_CREATED, conversation.getId(), me, userId));
        log.info("Direct conversation {} opened by {}", conversation.getId(), me);
        return views.conversation(me, actor.admin(), access.requireReadable(actor, conversation));
    }

    @Override
    @Transactional
    public ChatDtos.Conversation openSupport(Actor actor) {
        if (actor.admin()) {
            throw new ForbiddenActionException("Admins answer support conversations from the admin inbox");
        }
        UUID me = actor.userId();
        jdbc.update("""
                insert into chat_conversations (type, status, created_by, allow_member_invites, version, created_at, last_activity_at)
                values ('SUPPORT', 'OPEN', :me, false, 0, now(), now())
                on conflict (created_by) where type = 'SUPPORT' and status = 'OPEN' do nothing
                """, new MapSqlParameterSource("me", me));
        ChatConversation conversation = conversationRepository
                .findFirstByTypeAndCreatedByAndStatus(ConversationType.SUPPORT, me, ConversationStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Support conversation vanished for " + me));
        addParticipantIfAbsent(conversation.getId(), me, conversation.getLastMessageId() == null ? 0 : conversation.getLastMessageId());
        unhide(conversation.getId(), me);
        return views.conversation(me, false, access.requireReadable(actor, conversation));
    }

    @Override
    @Transactional
    public ChatDtos.Conversation openCourseChannel(Actor actor, String courseSlug) {
        Course course = courseRepository.findBySlug(courseSlug).orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        if (!eligibility.isEligible(actor.userId(), course.getId())) {
            throw ResourceNotFoundException.of("Course chat", courseSlug);
        }
        jdbc.update("""
                insert into chat_conversations (type, course_id, status, allow_member_invites, version, created_at, last_activity_at)
                values ('COURSE', :courseId, 'ACTIVE', false, 0, now(), now())
                on conflict (course_id) where type = 'COURSE' do nothing
                """, new MapSqlParameterSource("courseId", course.getId()));
        ChatConversation conversation = conversationRepository.findByTypeAndCourseId(ConversationType.COURSE, course.getId())
                .orElseThrow(() -> new IllegalStateException("Course channel vanished for course " + course.getId()));
        addParticipantIfAbsent(conversation.getId(), actor.userId(),
                conversation.getLastMessageId() == null ? 0 : conversation.getLastMessageId());
        unhide(conversation.getId(), actor.userId());
        return views.conversation(actor.userId(), actor.admin(), access.requireReadable(actor, conversation));
    }

    @Override
    @Transactional(readOnly = true)
    public ChatDtos.Conversation conversation(Actor actor, Integer conversationId) {
        return views.conversation(actor.userId(), actor.admin(), access.requireReadable(actor, conversationId));
    }

    @Override
    @Transactional
    public ChatDtos.Conversation updateConversation(Actor actor, Integer conversationId, ChatDtos.ConversationUpdateRequest request) {
        ChatAccess.View view = access.requireReadable(actor, conversationId);
        ChatParticipant participant = view.participant();
        if (participant == null || !participant.isActive()) {
            throw new BusinessRuleException("Open this conversation before changing its settings");
        }
        if (request.muted() != null) {
            participant.setMuted(request.muted());
        }
        participantRepository.saveAndFlush(participant);
        if (request.pinned() != null) {
            if (request.pinned() && participant.getPinnedAt() == null) {
                Integer pinnedCount = jdbc.queryForObject(
                        "select count(*) from chat_participants where user_id = :userId and left_at is null and pinned_at is not null",
                        new MapSqlParameterSource("userId", actor.userId()), Integer.class);
                if (pinnedCount != null && pinnedCount >= properties.maxPinnedConversations()) {
                    throw new BusinessRuleException("You can pin up to " + properties.maxPinnedConversations()
                            + " conversations. Unpin one first.");
                }
            }
            Timestamp pinnedAt = jdbc.queryForObject("update chat_participants set pinned_at = case when :pinned "
                            + "then coalesce(pinned_at, now()) end where conversation_id = :id and user_id = :userId returning pinned_at",
                    new MapSqlParameterSource("pinned", request.pinned()).addValue("id", conversationId)
                            .addValue("userId", actor.userId()), Timestamp.class);
            participant.setPinnedAt(pinnedAt == null ? null : pinnedAt.toInstant());
        }
        if (request.hidden() != null) {
            // The database clock, so it compares exactly with message times; the loaded row learns the same value,
            // so the response already says whether it's hidden
            Timestamp hiddenAt = jdbc.queryForObject("update chat_participants set hidden_at = case when :hidden then now() end "
                            + "where conversation_id = :id and user_id = :userId returning hidden_at",
                    new MapSqlParameterSource("hidden", request.hidden()).addValue("id", conversationId)
                            .addValue("userId", actor.userId()), Timestamp.class);
            participant.setHiddenAt(hiddenAt == null ? null : hiddenAt.toInstant());
        }
        return views.conversation(actor.userId(), actor.admin(), access.requireReadable(actor, conversationId));
    }

    @Override
    @Transactional(readOnly = true)
    public ChatDtos.MessagePage messages(Actor actor, Integer conversationId, Integer before, Integer after, int limit) {
        access.requireReadable(actor, conversationId);
        int size = clamp(limit, MAX_PAGE);
        UUID me = actor.userId();
        // Messages I deleted for myself are left out of my history
        List<ChatMessage> found;
        if (after != null) {
            found = messageRepository.findVisibleAfter(conversationId, after, me, size + 1);
        } else {
            found = before == null
                    ? messageRepository.findLatestVisible(conversationId, me, size + 1)
                    : messageRepository.findVisibleBefore(conversationId, before, me, size + 1);
        }
        boolean more = found.size() > size;
        List<ChatMessage> pageItems = new ArrayList<>(more ? found.subList(0, size) : found);
        if (after == null) {
            Collections.reverse(pageItems);
        }
        return new ChatDtos.MessagePage(views.messages(actor.userId(), pageItems), more);
    }

    @Override
    @Transactional
    public ChatDtos.Message send(Actor actor, Integer conversationId, ChatDtos.SendMessageRequest request) {
        UUID me = actor.userId();
        if (conversationId == null || request.clientMessageId() == null) {
            throw new BusinessRuleException("conversationId and clientMessageId are required");
        }
        String body = StringUtils.hasText(request.body()) ? request.body().strip() : null;
        List<UUID> fileIds = request.attachmentIds() == null ? List.of() : List.copyOf(new LinkedHashSet<>(request.attachmentIds()));
        if (body != null && body.length() > ChatDtos.MAX_BODY_LENGTH) {
            throw new BusinessRuleException("Messages can be at most 4000 characters");
        }
        if (fileIds.size() > properties.maxAttachments()) {
            throw new BusinessRuleException("Attach at most " + properties.maxAttachments() + " files");
        }
        if (body == null && fileIds.isEmpty()) {
            throw new BusinessRuleException("Write a message or attach a file");
        }

        ChatMessage existing = messageRepository.findBySenderIdAndClientMessageId(me, request.clientMessageId()).orElse(null);
        if (existing != null) {
            return sameMessage(existing, conversationId, me);
        }
        ChatAccess.View view = access.requireWritable(actor, conversationId);
        rateLimiter.check("chat-message", me, properties.messagesPerMinute(), TOO_FAST);
        Integer replyTo = request.replyToMessageId();
        if (replyTo != null && messageRepository.findById(replyTo)
                .filter(target -> target.getConversationId().equals(conversationId) && target.getKind() == MessageKind.TEXT)
                .isEmpty()) {
            throw new BusinessRuleException("You can only reply to a message in this conversation");
        }
        List<StoredFileResponse> files = new ArrayList<>();
        for (UUID fileId : fileIds) {
            StoredFileResponse file = fileService.requireOwnedFile(fileId, me, FilePurpose.CHAT_ATTACHMENT);
            if (attachmentRepository.existsByFileRef(file.url())) {
                throw new BusinessRuleException("This file was already sent. Upload it again to send it once more.");
            }
            files.add(file);
        }

        List<Integer> inserted = jdbc.queryForList("""
                insert into chat_messages (conversation_id, sender_id, client_message_id, kind, body, reply_to_message_id, created_at)
                values (:conversationId, :me, :clientId, 'TEXT', :body, :replyTo, now())
                on conflict (sender_id, client_message_id) where client_message_id is not null do nothing
                returning id
                """, new MapSqlParameterSource("conversationId", conversationId).addValue("me", me)
                .addValue("clientId", request.clientMessageId()).addValue("body", body).addValue("replyTo", replyTo), Integer.class);
        if (inserted.isEmpty()) {
            // A concurrent retry of the same send won the race
            ChatMessage winner = messageRepository.findBySenderIdAndClientMessageId(me, request.clientMessageId())
                    .orElseThrow(() -> new DuplicateResourceException("This message is already being sent"));
            return sameMessage(winner, conversationId, me);
        }
        ChatMessage message = messageRepository.findById(inserted.getFirst())
                .orElseThrow(() -> new IllegalStateException("Inserted message missing"));
        for (StoredFileResponse file : files) {
            attachmentRepository.save(ChatAttachment.builder()
                    .messageId(message.getId())
                    .fileRef(file.url())
                    .fileName(file.fileName())
                    .contentType(file.contentType())
                    .sizeBytes(file.size())
                    .build());
        }
        conversationRepository.advanceLastMessage(conversationId, message.getId(), message.getCreatedAt());
        if (view.participant() != null) {
            participantRepository.advanceRead(conversationId, me, message.getId());
        }
        notifyOffline(view, message, me, files.size());
        saveMentions(view, message, me, request.mentionUserIds(), files.size());
        events.publishEvent(ChatSignal.message(ChatSignal.Kind.MESSAGE_CREATED, conversationId, message.getId(), me));
        return views.message(me, message);
    }

    @Override
    @Transactional
    public int markRead(Actor actor, Integer conversationId, Integer messageId) {
        ChatAccess.View view = access.requireReadable(actor, conversationId);
        ChatConversation conversation = view.conversation();
        ChatParticipant participant = view.participant();
        if (participant == null && conversation.getType() == ConversationType.COURSE) {
            addParticipantIfAbsent(conversationId, actor.userId(), 0);
            participant = participantRepository.findByConversationIdAndUserId(conversationId, actor.userId()).orElse(null);
        }
        if (participant == null || !participant.isActive() || messageId == null) {
            return 0;
        }
        int last = conversation.getLastMessageId() == null ? 0 : conversation.getLastMessageId();
        int target = Math.min(messageId, last);
        int cursor = Math.max(participant.getLastReadMessageId(), target);
        if (participantRepository.advanceRead(conversationId, actor.userId(), target) > 0) {
            events.publishEvent(ChatSignal.cursor(ChatSignal.Kind.READ, conversationId, actor.userId(), cursor));
        }
        if (cursor >= last) {
            notificationService.markReadByLink(actor.userId(), NotificationType.CHAT_MESSAGE, link(conversationId));
        }
        return cursor;
    }

    @Override
    @Transactional
    public void markDelivered(Actor actor, Integer conversationId, Integer messageId) {
        ChatAccess.View view = access.requireReadable(actor, conversationId);
        if (view.participant() == null || messageId == null) {
            return;
        }
        int last = view.conversation().getLastMessageId() == null ? 0 : view.conversation().getLastMessageId();
        int target = Math.min(messageId, last);
        if (participantRepository.advanceDelivered(conversationId, actor.userId(), target) > 0
                && view.conversation().getType() == ConversationType.DIRECT) {
            events.publishEvent(ChatSignal.cursor(ChatSignal.Kind.DELIVERED, conversationId, actor.userId(), target));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void typing(Actor actor, Integer conversationId) {
        ChatAccess.View view = access.requireReadable(actor, conversationId);
        if (view.canSend()) {
            events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.TYPING, conversationId, actor.userId(), null));
        }
    }

    @Override
    @Transactional
    public ChatDtos.Message editMessage(Actor actor, Integer messageId, ChatDtos.EditMessageRequest request) {
        UUID me = actor.userId();
        ChatMessage message = messageRepository.findById(messageId).orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        // Editing is writing: blocked, archived, closed and restricted conversations refuse it, and the conversation
        // row is locked first, in the order sends use
        ChatAccess.View view = access.requireWritable(actor, message.getConversationId());
        if (message.getKind() != MessageKind.TEXT || !me.equals(message.getSenderId())) {
            throw new ForbiddenActionException("You can only edit your own messages");
        }
        if (message.getDeletedAt() != null || message.getHiddenAt() != null) {
            throw new BusinessRuleException("This message can't be edited any more");
        }
        if (message.getCreatedAt().plus(properties.editWindow()).isBefore(Instant.now())) {
            throw new BusinessRuleException("Messages can only be edited within "
                    + properties.editWindow().toMinutes() + " minutes of sending");
        }
        String body = request == null || !StringUtils.hasText(request.body()) ? null : request.body().strip();
        if (body != null && body.length() > ChatDtos.MAX_BODY_LENGTH) {
            throw new BusinessRuleException("Messages can be at most 4000 characters");
        }
        if (body == null && attachmentRepository.findByMessageId(messageId).isEmpty()) {
            throw new BusinessRuleException("Write something, or delete the message instead");
        }
        if (java.util.Objects.equals(body, message.getBody())) {
            return views.message(me, message);
        }
        jdbc.update("insert into chat_message_edits (message_id, body, edited_at) values (:messageId, :body, now())",
                new MapSqlParameterSource("messageId", messageId).addValue("body", message.getBody()));
        message.setBody(body);
        message.setEditedAt(Instant.now());
        messageRepository.save(message);
        // Left out, the mentions stay as they were; a list (even an empty one) replaces them
        if (request != null && request.mentionUserIds() != null) {
            saveMentions(view, message, me, request.mentionUserIds(), attachmentRepository.findByMessageId(messageId).size());
        }
        events.publishEvent(ChatSignal.message(ChatSignal.Kind.MESSAGE_UPDATED, message.getConversationId(), messageId, me));
        return views.message(me, message);
    }

    @Override
    @Transactional
    public ChatDtos.Message deleteMessage(Actor actor, Integer messageId) {
        ChatMessage message = messageRepository.findById(messageId).orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        access.requireReadable(actor, message.getConversationId());
        if (message.getKind() != MessageKind.TEXT || !actor.userId().equals(message.getSenderId())) {
            throw new ForbiddenActionException("You can only delete your own messages");
        }
        if (message.getDeletedAt() != null) {
            return views.message(actor.userId(), message);
        }
        if (message.getCreatedAt().plus(properties.deleteWindow()).isBefore(Instant.now())) {
            throw new BusinessRuleException("Messages can only be deleted within "
                    + properties.deleteWindow().toMinutes() + " minutes of sending");
        }
        message.setDeletedAt(Instant.now());
        message.setBody(null);
        messageRepository.save(message);
        removeAttachments(message);
        // Unsent means gone for everyone, earlier versions included
        jdbc.update("delete from chat_message_edits where message_id = :messageId", new MapSqlParameterSource("messageId", messageId));
        events.publishEvent(ChatSignal.message(ChatSignal.Kind.MESSAGE_UPDATED, message.getConversationId(), messageId, actor.userId()));
        return views.message(actor.userId(), message);
    }

    @Override
    @Transactional
    public void deleteForMe(Actor actor, Integer messageId) {
        ChatMessage message = messageRepository.findById(messageId).orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        access.requireReadable(actor, message.getConversationId());
        if (message.getKind() != MessageKind.TEXT) {
            throw new BusinessRuleException("Only messages people wrote can be deleted");
        }
        int added = jdbc.update(
                "insert into chat_message_hidden_for (message_id, user_id, created_at) values (:messageId, :me, now()) on conflict do nothing",
                new MapSqlParameterSource("messageId", messageId).addValue("me", actor.userId()));
        if (added > 0) {
            // My other tabs and devices drop it too
            events.publishEvent(new ChatSignal(ChatSignal.Kind.MESSAGE_REMOVED, message.getConversationId(), messageId,
                    actor.userId(), actor.userId(), null));
        }
    }

    @Override
    @Transactional
    public ChatDtos.Message react(Actor actor, Integer messageId, String emoji) {
        UUID me = actor.userId();
        ChatMessage message = messageRepository.findById(messageId).orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        // Reacting is taking part, like sending: blocked, archived, closed and restricted conversations refuse it.
        // This also locks the conversation row first, in the order sends use.
        access.requireWritable(actor, message.getConversationId());
        if (hiddenFor(messageId, me)) {
            throw ResourceNotFoundException.of("Message", messageId);
        }
        if (message.getKind() != MessageKind.TEXT || message.getDeletedAt() != null || message.getHiddenAt() != null) {
            throw new BusinessRuleException("You can't react to this message");
        }
        String chosen = ChatReactions.normalize(emoji);
        if (chosen == null) {
            throw new BusinessRuleException("Choose one of the offered reactions");
        }
        rateLimiter.check("chat-reaction", me, properties.reactionsPerMinute(), TOO_MANY_REACTIONS);
        int changed = jdbc.update("""
                insert into chat_message_reactions (message_id, user_id, emoji, created_at)
                values (:messageId, :me, :emoji, now())
                on conflict (message_id, user_id) do update set emoji = excluded.emoji, created_at = excluded.created_at
                where chat_message_reactions.emoji is distinct from excluded.emoji
                """, new MapSqlParameterSource("messageId", messageId).addValue("me", me).addValue("emoji", chosen));
        if (changed > 0) {
            events.publishEvent(ChatSignal.message(ChatSignal.Kind.MESSAGE_UPDATED, message.getConversationId(), messageId, me));
        }
        return views.message(me, message);
    }

    @Override
    @Transactional
    public ChatDtos.Message unreact(Actor actor, Integer messageId) {
        UUID me = actor.userId();
        ChatMessage message = messageRepository.findById(messageId).orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        access.requireReadable(actor, message.getConversationId());
        int removed = jdbc.update("delete from chat_message_reactions where message_id = :messageId and user_id = :me",
                new MapSqlParameterSource("messageId", messageId).addValue("me", me));
        if (removed > 0) {
            events.publishEvent(ChatSignal.message(ChatSignal.Kind.MESSAGE_UPDATED, message.getConversationId(), messageId, me));
        }
        return views.message(me, message);
    }

    @Override
    @Transactional
    public ChatDtos.Conversation pin(Actor actor, Integer messageId) {
        ChatMessage message = messageRepository.findById(messageId).orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        Integer conversationId = message.getConversationId();
        // Locks the conversation row, so two pins at once can't both get past the limit
        ChatAccess.View view = access.requireWritable(actor, conversationId);
        if (!access.canPin(view, actor.userId())) {
            throw new ForbiddenActionException("You can't pin messages in this conversation");
        }
        if (message.getKind() != MessageKind.TEXT || message.getDeletedAt() != null || message.getHiddenAt() != null) {
            throw new BusinessRuleException("This message can't be pinned");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("conversationId", conversationId)
                .addValue("messageId", messageId).addValue("me", actor.userId());
        Integer pinned = jdbc.queryForObject("""
                select count(*) from chat_pins p join chat_messages m on m.id = p.message_id
                where p.conversation_id = :conversationId and p.message_id <> :messageId
                  and m.deleted_at is null and m.hidden_at is null
                """, params, Integer.class);
        if (pinned != null && pinned >= properties.maxPins()) {
            throw new BusinessRuleException("A conversation can have " + properties.maxPins() + " pinned messages. Unpin one first.");
        }
        int added = jdbc.update("""
                insert into chat_pins (conversation_id, message_id, pinned_by, pinned_at)
                values (:conversationId, :messageId, :me, now())
                on conflict do nothing
                """, params);
        if (added > 0) {
            events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.CONVERSATION_UPDATED, conversationId, actor.userId(), null));
        }
        return views.conversation(actor.userId(), actor.admin(), access.requireReadable(actor, conversationId));
    }

    @Override
    @Transactional
    public ChatDtos.Conversation unpin(Actor actor, Integer messageId) {
        ChatMessage message = messageRepository.findById(messageId).orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        Integer conversationId = message.getConversationId();
        ChatAccess.View view = access.requireWritable(actor, conversationId);
        if (!access.canPin(view, actor.userId())) {
            throw new ForbiddenActionException("You can't unpin messages in this conversation");
        }
        int removed = jdbc.update("delete from chat_pins where conversation_id = :conversationId and message_id = :messageId",
                new MapSqlParameterSource("conversationId", conversationId).addValue("messageId", messageId));
        if (removed > 0) {
            events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.CONVERSATION_UPDATED, conversationId, actor.userId(), null));
        }
        return views.conversation(actor.userId(), actor.admin(), access.requireReadable(actor, conversationId));
    }

    @Override
    @Transactional
    public ChatDtos.Message hideMessage(Actor actor, Integer messageId) {
        ChatMessage message = messageRepository.findById(messageId).orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        ChatAccess.View view = access.requireReadable(actor, message.getConversationId());
        boolean allowed = switch (view.conversation().getType()) {
            case GROUP -> view.participant() != null
                    && (view.participant().getRole() == ParticipantRole.OWNER || view.participant().getRole() == ParticipantRole.ADMIN);
            case COURSE -> eligibility.isStaff(actor.userId(), view.conversation().getCourseId());
            default -> false;
        };
        if (!allowed) {
            throw new ForbiddenActionException("You can't hide messages in this conversation");
        }
        if (message.getKind() != MessageKind.TEXT) {
            throw new BusinessRuleException("Only messages people wrote can be hidden");
        }
        if (message.getHiddenAt() == null) {
            message.setHiddenAt(Instant.now());
            message.setHiddenBy(actor.userId());
            messageRepository.save(message);
            events.publishEvent(ChatSignal.message(ChatSignal.Kind.MESSAGE_UPDATED, message.getConversationId(), messageId, actor.userId()));
            log.info("Chat message {} hidden by {}", messageId, actor.userId());
        }
        return views.message(actor.userId(), message);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatDtos.MemberPage courseMembers(Actor actor, String courseSlug, String query, String cursor, int limit) {
        Course course = courseRepository.findBySlug(courseSlug).orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        if (!eligibility.isEligible(actor.userId(), course.getId())) {
            throw ResourceNotFoundException.of("Course", courseSlug);
        }
        rateLimiter.check("chat-directory", actor.userId(), properties.directoryQueriesPerMinute(),
                "You're searching too quickly. Wait a moment and try again.");
        int size = clamp(limit, MAX_PAGE);
        boolean viewerStaff = userRepository.findById(actor.userId())
                .map(user -> user.getRole() == Role.ROLE_ADMIN || user.getRole() == Role.ROLE_INSTRUCTOR)
                .orElse(false);
        MapSqlParameterSource params = new MapSqlParameterSource("courseId", course.getId())
                .addValue("me", actor.userId())
                .addValue("viewerStaff", viewerStaff)
                .addValue("limit", size + 1);
        StringBuilder filters = new StringBuilder();
        if (StringUtils.hasText(query)) {
            String term = query.trim();
            if (term.length() > 50) {
                term = term.substring(0, 50);
            }
            params.addValue("prefix", term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            // Learners are shown as "Jane L.", so other learners can only search by first name
            filters.append(" and (u.first_name ilike :prefix or ((:viewerStaff or u.role in ('ROLE_ADMIN', 'ROLE_INSTRUCTOR'))"
                    + " and (u.last_name ilike :prefix or (u.first_name || ' ' || u.last_name) ilike :prefix)))");
        }
        if (StringUtils.hasText(cursor)) {
            String[] parts = decodeCursor(cursor);
            try {
                params.addValue("afterKey", parts[0]).addValue("afterId", UUID.fromString(parts[1]));
            } catch (RuntimeException ex) {
                throw new BusinessRuleException("Invalid cursor");
            }
            filters.append(" and (sort_key, u.id) > (:afterKey, :afterId)");
        }
        record Row(UUID id, String sortKey) {
        }
        List<Row> rows = jdbc.query("""
                select u.id, sort_key
                from courses c
                join users u on %s
                cross join lateral (select lower(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) as sort_key) k
                where c.id = :courseId and u.id <> :me
                  and not exists (select 1 from chat_blocks b where b.blocker_id = u.id and b.blocked_id = :me)
                  %s
                order by sort_key, u.id
                limit :limit
                """.formatted(ChatEligibility.ELIGIBLE_SQL, filters), params,
                (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("sort_key")));
        boolean more = rows.size() > size;
        List<Row> pageRows = more ? rows.subList(0, size) : rows;
        Map<UUID, ChatDtos.Person> named = people.lookup(actor.userId(), pageRows.stream().map(Row::id).toList());
        List<ChatDtos.Person> items = pageRows.stream().map(row -> named.get(row.id())).filter(java.util.Objects::nonNull).toList();
        String next = more ? encodeCursor(pageRows.getLast().sortKey(), pageRows.getLast().id().toString()) : null;
        return new ChatDtos.MemberPage(items, next);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatDtos.Settings settings(Actor actor) {
        ChatSettings settings = settingsOf(actor.userId());
        return new ChatDtos.Settings(ChatViews.lower(settings.getDirectMessages()), settings.isShowPresence());
    }

    @Override
    @Transactional
    public ChatDtos.Settings updateSettings(Actor actor, ChatDtos.SettingsRequest request) {
        DirectMessagePolicy policy;
        try {
            policy = DirectMessagePolicy.valueOf(request.directMessages().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Choose course_members, staff_only or nobody");
        }
        ChatSettings settings = settingsOf(actor.userId());
        boolean presenceChanged = request.showPresence() != null && request.showPresence() != settings.isShowPresence();
        settings.setDirectMessages(policy);
        if (request.showPresence() != null) {
            settings.setShowPresence(request.showPresence());
        }
        settingsRepository.save(settings);
        if (presenceChanged) {
            // Contacts start or stop seeing my status
            events.publishEvent(ChatSignal.presence(actor.userId()));
        }
        return new ChatDtos.Settings(ChatViews.lower(policy), settings.isShowPresence());
    }

    /** The user's saved settings, or the defaults for someone who never changed them. */
    private ChatSettings settingsOf(UUID userId) {
        return settingsRepository.findById(userId).orElseGet(() -> ChatSettings.builder()
                .userId(userId)
                .directMessages(DirectMessagePolicy.COURSE_MEMBERS)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatDtos.BlockedUser> blocks(Actor actor) {
        List<ChatBlock> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDesc(actor.userId());
        Map<UUID, ChatDtos.Person> named = people.lookup(actor.userId(), blocks.stream().map(ChatBlock::getBlockedId).toList());
        return blocks.stream()
                .filter(block -> named.containsKey(block.getBlockedId()))
                .map(block -> new ChatDtos.BlockedUser(named.get(block.getBlockedId()), block.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void block(Actor actor, UUID userId) {
        if (actor.userId().equals(userId)) {
            throw new BusinessRuleException("You can't block yourself");
        }
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.of("User", userId);
        }
        jdbc.update("insert into chat_blocks (blocker_id, blocked_id, created_at) values (:me, :other, now()) on conflict do nothing",
                new MapSqlParameterSource("me", actor.userId()).addValue("other", userId));
    }

    @Override
    @Transactional
    public void unblock(Actor actor, UUID userId) {
        jdbc.update("delete from chat_blocks where blocker_id = :me and blocked_id = :other",
                new MapSqlParameterSource("me", actor.userId()).addValue("other", userId));
    }

    @Override
    @Transactional
    public void eraseAccount(UUID userId) {
        groupService.leaveAllForDeletedAccount(userId);
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        List<String> files = jdbc.queryForList("""
                select a.file_ref from chat_attachments a join chat_messages m on m.id = a.message_id
                where m.sender_id = :userId
                """, params, String.class);
        files.forEach(file -> fileService.release(file, userId));
        jdbc.update("delete from chat_attachments a using chat_messages m where m.id = a.message_id and m.sender_id = :userId", params);
        jdbc.update("delete from chat_message_edits e using chat_messages m where m.id = e.message_id and m.sender_id = :userId", params);
        jdbc.update("delete from chat_message_mentions mm using chat_messages m where m.id = mm.message_id and m.sender_id = :userId", params);
        jdbc.update("delete from chat_message_mentions where user_id = :userId", params);
        jdbc.update("""
                update chat_messages set body = null, deleted_at = coalesce(deleted_at, now()), sender_id = null
                where sender_id = :userId
                """, params);
        jdbc.update("""
                update chat_participants set left_at = now(), left_reason = 'LEFT', role = 'MEMBER'
                where user_id = :userId and left_at is null
                """, params);
        jdbc.update("update chat_conversations set status = 'CLOSED' where type = 'SUPPORT' and created_by = :userId and status = 'OPEN'",
                params);
        jdbc.update("delete from chat_blocks where blocker_id = :userId or blocked_id = :userId", params);
        jdbc.update("delete from chat_message_hidden_for where user_id = :userId", params);
        jdbc.update("delete from chat_message_reactions where user_id = :userId", params);
        jdbc.update("update chat_pins set pinned_by = null where pinned_by = :userId", params);
        jdbc.update("delete from chat_presence where user_id = :userId", params);
        jdbc.update("delete from chat_settings where user_id = :userId", params);
        jdbc.update("delete from chat_restrictions where user_id = :userId", params);
        log.info("Erased chat data of deleted account {}", userId);
    }

    // ---- helpers ----

    private boolean mayStartDirect(UUID me, UUID other) {
        if (eligibility.sharedCourseIds(me, other).isEmpty() || blockRepository.existsBetween(me, other)) {
            return false;
        }
        return policyOf(other) == DirectMessagePolicy.COURSE_MEMBERS || eligibility.isStaffOfSharedCourse(me, other);
    }

    private boolean hiddenFor(Integer messageId, UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from chat_message_hidden_for where message_id = :messageId and user_id = :userId)",
                new MapSqlParameterSource("messageId", messageId).addValue("userId", userId), Boolean.class));
    }

    private DirectMessagePolicy policyOf(UUID userId) {
        return settingsRepository.findById(userId).map(ChatSettings::getDirectMessages).orElse(DirectMessagePolicy.COURSE_MEMBERS);
    }

    private ChatDtos.Message sameMessage(ChatMessage message, Integer conversationId, UUID me) {
        if (!message.getConversationId().equals(conversationId)) {
            throw new DuplicateResourceException("This clientMessageId was already used for another message");
        }
        return views.message(me, message);
    }

    /** Offline delivery: one unread notification per conversation, which digests by email. Course channels don't notify. */
    private void notifyOffline(ChatAccess.View view, ChatMessage message, UUID sender, int attachments) {
        ChatConversation conversation = view.conversation();
        String senderName = people.publicName(sender);
        String preview = ChatViews.preview(message, attachments);
        String link = link(conversation.getId());
        switch (conversation.getType()) {
            case DIRECT -> {
                if (view.peerId() != null && !isMuted(conversation.getId(), view.peerId())) {
                    notificationService.notifyUsersOnce(List.of(view.peerId()), NotificationType.CHAT_MESSAGE,
                            senderName + " sent you a message", preview, link);
                }
            }
            case GROUP -> {
                Set<UUID> recipients = new HashSet<>();
                participantRepository.findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(conversation.getId()).stream()
                        .filter(member -> !member.getUserId().equals(sender) && !member.isMuted())
                        .forEach(member -> recipients.add(member.getUserId()));
                Set<UUID> eligible = eligibility.eligibleAmong(conversation.getCourseId(), recipients);
                notificationService.notifyUsersOnce(eligible, NotificationType.CHAT_MESSAGE,
                        "New message in " + conversation.getTitle(), senderName + ": " + preview, link);
            }
            case SUPPORT -> {
                if (sender.equals(conversation.getCreatedBy())) {
                    notificationService.notifyUsersOnce(userRepository.findEnabledIdsByRole(Role.ROLE_ADMIN),
                            NotificationType.CHAT_MESSAGE, "New support message from " + senderName, preview,
                            "/admin/chat/support/" + conversation.getId());
                } else if (conversation.getCreatedBy() != null && !isMuted(conversation.getId(), conversation.getCreatedBy())) {
                    notificationService.notifyUsersOnce(List.of(conversation.getCreatedBy()), NotificationType.CHAT_MESSAGE,
                            "Support replied", preview, link);
                }
            }
            case COURSE -> {
                // Too noisy to notify a whole course; the channel shows unread counts instead
            }
        }
    }

    /**
     * Keeps who a message @mentions: in group and course chats only, people who can read it, never the sender.
     * Those newly mentioned are notified, muted or not, unless they blocked the sender. On an edit, people no
     * longer in the list are dropped and only new ones are notified.
     */
    private void saveMentions(ChatAccess.View view, ChatMessage message, UUID sender, List<UUID> requested, int attachments) {
        ChatConversation conversation = view.conversation();
        boolean mentionable = conversation.getType() == ConversationType.GROUP || conversation.getType() == ConversationType.COURSE;
        Set<UUID> wanted = new LinkedHashSet<>(requested == null ? List.of() : requested);
        wanted.remove(null);
        wanted.remove(sender);
        Set<UUID> allowed = !mentionable || wanted.isEmpty() ? Set.of() : access.readersAmong(conversation, wanted);
        MapSqlParameterSource params = new MapSqlParameterSource("messageId", message.getId());
        Set<UUID> existing = new HashSet<>(jdbc.queryForList(
                "select user_id from chat_message_mentions where message_id = :messageId", params, UUID.class));
        if (allowed.isEmpty()) {
            jdbc.update("delete from chat_message_mentions where message_id = :messageId", params);
        } else {
            jdbc.update("delete from chat_message_mentions where message_id = :messageId and user_id not in (:keep)",
                    new MapSqlParameterSource("messageId", message.getId()).addValue("keep", allowed));
        }
        List<UUID> added = allowed.stream().filter(userId -> !existing.contains(userId)).toList();
        if (added.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("insert into chat_message_mentions (message_id, user_id) values (:messageId, :userId) on conflict do nothing",
                added.stream().map(userId -> new MapSqlParameterSource("messageId", message.getId()).addValue("userId", userId))
                        .toArray(MapSqlParameterSource[]::new));
        Set<UUID> blockedMe = new HashSet<>(jdbc.queryForList(
                "select blocker_id from chat_blocks where blocked_id = :sender and blocker_id in (:added)",
                new MapSqlParameterSource("sender", sender).addValue("added", added), UUID.class));
        List<UUID> recipients = added.stream().filter(userId -> !blockedMe.contains(userId)).toList();
        if (recipients.isEmpty()) {
            return;
        }
        String where = conversation.getType() == ConversationType.COURSE && view.course() != null
                ? view.course().getTitle() : conversation.getTitle();
        notificationService.notifyUsers(recipients, NotificationType.CHAT_MENTION,
                people.publicName(sender) + " mentioned you in " + where, ChatViews.preview(message, attachments),
                link(conversation.getId()) + "?message=" + message.getId());
    }

    private boolean isMuted(Integer conversationId, UUID userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId).map(ChatParticipant::isMuted).orElse(false);
    }

    private void removeAttachments(ChatMessage message) {
        List<ChatAttachment> files = attachmentRepository.findByMessageId(message.getId());
        for (ChatAttachment file : files) {
            if (message.getSenderId() != null) {
                fileService.release(file.getFileRef(), message.getSenderId());
            }
        }
        attachmentRepository.deleteAll(files);
    }

    private void addParticipantIfAbsent(Integer conversationId, UUID userId, int cursor) {
        jdbc.update("""
                insert into chat_participants (conversation_id, user_id, role, joined_at, last_read_message_id,
                                               last_delivered_message_id, muted)
                values (:conversationId, :userId, 'MEMBER', now(), :cursor, :cursor, false)
                on conflict do nothing
                """, new MapSqlParameterSource("conversationId", conversationId).addValue("userId", userId).addValue("cursor", cursor));
    }

    private void unhide(Integer conversationId, UUID userId) {
        jdbc.update("update chat_participants set hidden_at = null where conversation_id = :id and user_id = :userId and hidden_at is not null",
                new MapSqlParameterSource("id", conversationId).addValue("userId", userId));
    }

    private long countSince(String sql, UUID userId, Duration window) {
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("me", userId)
                .addValue("since", Timestamp.from(Instant.now().minus(window))), Long.class);
        return count == null ? 0 : count;
    }

    static String link(Integer conversationId) {
        return "/chat/" + conversationId;
    }

    static String directKey(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
    }

    static int clamp(int requested, int fallback) {
        if (requested <= 0) {
            return fallback;
        }
        return Math.min(requested, MAX_PAGE);
    }

    static String encodeCursor(String first, String second) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((first + "\u0001" + second).getBytes(StandardCharsets.UTF_8));
    }

    static String[] decodeCursor(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\u0001", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return parts;
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid cursor");
        }
    }
}
