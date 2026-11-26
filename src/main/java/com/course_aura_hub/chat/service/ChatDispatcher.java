package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.config.ChatProperties;
import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.model.ChatConversation;
import com.course_aura_hub.chat.model.ChatGroupInvite;
import com.course_aura_hub.chat.model.ChatMessage;
import com.course_aura_hub.chat.model.ChatParticipant;
import com.course_aura_hub.chat.model.ConversationType;
import com.course_aura_hub.chat.repository.ChatConversationRepository;
import com.course_aura_hub.chat.repository.ChatGroupInviteRepository;
import com.course_aura_hub.chat.repository.ChatMessageRepository;
import com.course_aura_hub.chat.repository.ChatParticipantRepository;
import com.course_aura_hub.common.cluster.ClusterChannelHandler;
import com.course_aura_hub.common.cluster.ClusterMessageBus;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Pushes chat events to WebSocket clients after commit. Each signal is routed on this instance, then relayed to
 * the other instances over Postgres NOTIFY (ids only); every instance works out, for the users connected to it,
 * who may see the event right now, so a removed member stops receiving messages at once.
 *
 * <p>Best effort: the REST API is the source of truth and clients reload history when they reconnect.</p>
 */
@Slf4j
@Component
public class ChatDispatcher implements ClusterChannelHandler {

    public static final String USER_QUEUE = "/queue/chat";
    static final String CHANNEL = "course_aura_chat";

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final ClusterMessageBus bus;
    private final ChatProperties properties;
    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatGroupInviteRepository inviteRepository;
    private final CourseRepository courseRepository;
    private final ChatAccess access;
    private final ChatPeople people;
    private final ChatViews views;
    private final ChatPresence presence;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate readOnly;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(5_000), Thread.ofPlatform().name("chat-dispatch-", 0).daemon().factory());

    public ChatDispatcher(
            SimpMessagingTemplate messagingTemplate,
            SimpUserRegistry userRegistry,
            ClusterMessageBus bus,
            ChatProperties properties,
            ChatConversationRepository conversationRepository,
            ChatParticipantRepository participantRepository,
            ChatMessageRepository messageRepository,
            ChatGroupInviteRepository inviteRepository,
            CourseRepository courseRepository,
            ChatAccess access,
            ChatPeople people,
            ChatViews views,
            ChatPresence presence,
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
        this.bus = bus;
        this.properties = properties;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.inviteRepository = inviteRepository;
        this.courseRepository = courseRepository;
        this.access = access;
        this.people = people;
        this.views = views;
        this.presence = presence;
        this.jdbc = jdbc;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public boolean enabled() {
        return properties.clusterFanout();
    }

    /** Signals published outside a transaction (typing) are handled at once. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSignal(ChatSignal signal) {
        submit(() -> {
            deliverLocally(signal);
            if (properties.clusterFanout()) {
                bus.publish(CHANNEL, signal.encode());
            }
        });
    }

    @Override
    public void onMessage(String body) {
        ChatSignal signal = ChatSignal.decode(body);
        if (signal != null) {
            submit(() -> deliverLocally(signal));
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void submit(Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException ex) {
                    log.warn("Chat event delivery failed", ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            log.warn("Chat event dropped: dispatch queue is full");
        }
    }

    /** Sends the signal to the users connected to this instance who may see it. */
    void deliverLocally(ChatSignal signal) {
        Set<UUID> online = onlineUsers();
        if (online.isEmpty()) {
            return;
        }
        readOnly.executeWithoutResult(status -> route(signal, online));
    }

    private void route(ChatSignal signal, Set<UUID> online) {
        switch (signal.kind()) {
            case INVITE_RECEIVED -> inviteRepository.findById(signal.refId())
                    .filter(invite -> online.contains(invite.getInviteeId()))
                    .ifPresent(invite -> sendInvite(invite));
            case INVITE_REVOKED -> {
                if (online.contains(signal.targetId())) {
                    send(signal.targetId(), signal.kind().eventType(),
                            Map.of("inviteId", signal.refId(), "conversationId", signal.conversationId()));
                }
            }
            case MESSAGE_REMOVED -> {
                if (online.contains(signal.targetId())) {
                    send(signal.targetId(), signal.kind().eventType(),
                            Map.of("conversationId", signal.conversationId(), "messageId", signal.refId()));
                    sendUnread(signal.targetId());
                }
            }
            case ACCESS_LOST -> {
                if (online.contains(signal.targetId())) {
                    send(signal.targetId(), signal.kind().eventType(), Map.of("conversationId", signal.conversationId()));
                    sendUnread(signal.targetId());
                }
            }
            case PRESENCE -> sendPresence(signal.actorId(), online);
            default -> conversationRepository.findById(signal.conversationId())
                    .ifPresent(conversation -> routeConversation(signal, conversation, online));
        }
    }

    private void routeConversation(ChatSignal signal, ChatConversation conversation, Set<UUID> online) {
        Set<UUID> readers = access.readersAmong(conversation, online);
        switch (signal.kind()) {
            case MESSAGE_CREATED, MESSAGE_UPDATED -> {
                ChatMessage message = messageRepository.findById(signal.refId()).orElse(null);
                if (message == null) {
                    return;
                }
                // Someone who deleted a message for themselves doesn't get its later updates
                Set<UUID> removed = signal.kind() == ChatSignal.Kind.MESSAGE_UPDATED
                        ? hiddenFor(message.getId(), readers) : Set.of();
                for (UUID reader : readers) {
                    if (removed.contains(reader)) {
                        continue;
                    }
                    send(reader, signal.kind().eventType(), views.message(reader, message));
                    if (signal.kind() == ChatSignal.Kind.MESSAGE_CREATED && !reader.equals(message.getSenderId())) {
                        sendUnread(reader);
                    }
                }
            }
            case READ -> {
                if (conversation.getType() == ConversationType.DIRECT) {
                    Map<String, Object> data = cursorData(signal);
                    readers.forEach(reader -> send(reader, "read", data));
                }
                if (readers.contains(signal.actorId())) {
                    sendUnread(signal.actorId());
                }
            }
            case DELIVERED -> {
                Map<String, Object> data = cursorData(signal);
                readers.forEach(reader -> send(reader, "delivered", data));
            }
            case TYPING -> {
                for (UUID reader : readers) {
                    if (!reader.equals(signal.actorId())) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("conversationId", conversation.getId());
                        data.put("user", people.one(reader, signal.actorId()));
                        send(reader, "typing", data);
                    }
                }
            }
            case CONVERSATION_CREATED -> {
                if (signal.targetId() != null && readers.contains(signal.targetId())) {
                    sendConversation(signal.targetId(), "conversation.created", conversation);
                }
            }
            case CONVERSATION_UPDATED -> readers.forEach(reader -> sendConversation(reader, "conversation.updated", conversation));
            case MEMBER_JOINED, MEMBER_LEFT, MEMBER_ROLE -> {
                Set<UUID> recipients = new HashSet<>(readers);
                if (signal.kind() == ChatSignal.Kind.MEMBER_LEFT) {
                    recipients.remove(signal.targetId());
                }
                ChatParticipant member = signal.targetId() == null ? null
                        : participantRepository.findByConversationIdAndUserId(conversation.getId(), signal.targetId()).orElse(null);
                for (UUID recipient : recipients) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("conversationId", conversation.getId());
                    data.put("user", people.one(recipient, signal.targetId()));
                    data.put("role", member == null || !member.isActive() ? null : ChatViews.lower(member.getRole()));
                    send(recipient, signal.kind().eventType(), data);
                }
            }
            default -> {
                // Targeted kinds are handled in route()
            }
        }
    }

    /** Tells the people connected here who share a direct conversation with the user (and no block) their status. */
    private void sendPresence(UUID userId, Set<UUID> online) {
        Set<UUID> candidates = new HashSet<>(online);
        candidates.remove(userId);
        if (userId == null || candidates.isEmpty()) {
            return;
        }
        List<UUID> contacts = jdbc.queryForList("""
                select distinct other.user_id
                from chat_participants me
                join chat_conversations c on c.id = me.conversation_id and c.type = 'DIRECT'
                join chat_participants other on other.conversation_id = c.id and other.user_id <> me.user_id
                where me.user_id = :userId and me.left_at is null and other.left_at is null
                  and other.user_id in (:candidates)
                  and not exists (select 1 from chat_blocks b
                                  where (b.blocker_id = me.user_id and b.blocked_id = other.user_id)
                                     or (b.blocker_id = other.user_id and b.blocked_id = me.user_id))
                """, new MapSqlParameterSource("userId", userId).addValue("candidates", candidates), UUID.class);
        if (contacts.isEmpty()) {
            return;
        }
        // Null when they hide their status: contacts then stop showing it
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("presence", presence.shared(userId));
        contacts.forEach(contact -> send(contact, "presence", data));
    }

    private void sendInvite(ChatGroupInvite invite) {
        ChatConversation group = conversationRepository.findById(invite.getConversationId()).orElse(null);
        if (group == null) {
            return;
        }
        Course course = courseRepository.findById(group.getCourseId()).orElse(null);
        UUID invitee = invite.getInviteeId();
        Map<UUID, ChatDtos.Person> named = people.lookup(invitee, List.of(invite.getInviterId(), invitee));
        send(invitee, "invite.received", new ChatDtos.Invite(invite.getId(), group.getId(), group.getTitle(),
                course == null ? null : new ChatDtos.CourseRef(course.getId(), course.getSlug(), course.getTitle()),
                named.get(invite.getInviterId()), named.get(invitee), ChatViews.lower(invite.getStatus()),
                invite.getCreatedAt(), invite.getExpiresAt()));
    }

    private void sendConversation(UUID userId, String type, ChatConversation conversation) {
        Actor actor = actorOf(userId);
        if (actor == null) {
            return;
        }
        try {
            send(userId, type, views.conversation(userId, actor.admin(), access.requireReadable(actor, conversation)));
        } catch (ResourceNotFoundException | ForbiddenActionException ex) {
            // Lost access between routing and loading; nothing to send
        }
    }

    private Set<UUID> hiddenFor(Integer messageId, Set<UUID> readers) {
        if (readers.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "select user_id from chat_message_hidden_for where message_id = :messageId and user_id in (:readers)",
                new MapSqlParameterSource("messageId", messageId).addValue("readers", readers), UUID.class));
    }

    private void sendUnread(UUID userId) {
        send(userId, "unread", new ChatDtos.UnreadCount(views.totalUnread(userId)));
    }

    private static Map<String, Object> cursorData(ChatSignal signal) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversationId", signal.conversationId());
        data.put("userId", signal.actorId());
        data.put("messageId", signal.value());
        return data;
    }

    private Actor actorOf(UUID userId) {
        return userRegistry.getUser(userId.toString()) == null ? null
                : new Actor(userId, null, access.isAdmin(userId));
    }

    private void send(UUID userId, String type, Object data) {
        messagingTemplate.convertAndSendToUser(userId.toString(), USER_QUEUE, new ChatDtos.Event(type, data));
    }

    private Set<UUID> onlineUsers() {
        Set<UUID> online = new HashSet<>();
        for (SimpUser user : userRegistry.getUsers()) {
            try {
                online.add(UUID.fromString(user.getName()));
            } catch (IllegalArgumentException ex) {
                // Not a chat principal
            }
        }
        return online;
    }
}
