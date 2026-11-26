package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.model.ChatConversation;
import com.course_aura_hub.chat.model.ChatParticipant;
import com.course_aura_hub.chat.model.ChatRestriction;
import com.course_aura_hub.chat.model.ConversationStatus;
import com.course_aura_hub.chat.model.ConversationType;
import com.course_aura_hub.chat.repository.ChatBlockRepository;
import com.course_aura_hub.chat.repository.ChatConversationRepository;
import com.course_aura_hub.chat.repository.ChatParticipantRepository;
import com.course_aura_hub.chat.repository.ChatRestrictionRepository;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who may read and write each conversation. Runs inside the caller's transaction.
 *
 * <ul>
 *     <li>DIRECT: its two participants read; they write while they share a course and neither blocked the other.</li>
 *     <li>COURSE: eligible members of the course read; they write while the course is published.</li>
 *     <li>GROUP: active, eligible members read; they write while the group is active and the course published.</li>
 *     <li>SUPPORT: the requester and any admin read; they write while the thread is open.</li>
 * </ul>
 * Nobody else can see that a conversation exists: they get 404. Restricted users can read but not write.
 */
@Component
@RequiredArgsConstructor
public class ChatAccess {

    static final String RESTRICTED = "Your chat access is restricted until %s";
    static final String DIRECT_READ_ONLY = "You can't send messages in this conversation";
    static final String COURSE_INACTIVE = "This course is no longer active";
    static final String GROUP_ARCHIVED = "This group was archived";
    static final String SUPPORT_CLOSED = "This conversation was closed. Start a new one to contact support.";
    private static final DateTimeFormatter RESTRICTION_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatRestrictionRepository restrictionRepository;
    private final ChatBlockRepository blockRepository;
    private final CourseRepository courseRepository;
    private final ChatEligibility eligibility;
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * A conversation as one person may use it.
     *
     * @param participant    their membership row, or null (admins in support, course members who never opened it)
     * @param course         the course of a COURSE or GROUP conversation
     * @param peerId         the other person of a DIRECT conversation
     * @param readOnlyReason why they can't write, or null when they can
     */
    public record View(ChatConversation conversation, ChatParticipant participant, Course course, UUID peerId,
                       String readOnlyReason) {

        public boolean canSend() {
            return readOnlyReason == null;
        }
    }

    public View requireReadable(Actor actor, Integer conversationId) {
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> notFound(conversationId));
        return requireReadable(actor, conversation);
    }

    public View requireReadable(Actor actor, ChatConversation conversation) {
        return view(actor, conversation).orElseThrow(() -> notFound(conversation.getId()));
    }

    /**
     * Like {@link #requireReadable}, refusing with 403 when the person can't write. Locks the conversation row
     * first ({@code for no key update}), the same order membership changes take, so a send and a removal of
     * the same member serialize instead of deadlocking: a removal that commits first wins, a later one waits.
     */
    public View requireWritable(Actor actor, Integer conversationId) {
        List<Integer> locked = jdbc.queryForList(
                "select id from chat_conversations where id = :id for no key update",
                new MapSqlParameterSource("id", conversationId), Integer.class);
        if (locked.isEmpty()) {
            throw notFound(conversationId);
        }
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> notFound(conversationId));
        View view = view(actor, conversation).orElseThrow(() -> notFound(conversationId));
        if (!view.canSend()) {
            throw new ForbiddenActionException(view.readOnlyReason());
        }
        return view;
    }

    /** Null when the user may write in chat, otherwise why not. */
    public String restrictionReason(UUID userId) {
        return restrictionRepository.findByUserIdAndUntilAfter(userId, Instant.now())
                .map(ChatRestriction::getUntil)
                .map(until -> RESTRICTED.formatted(RESTRICTION_DATE.format(until)))
                .orElse(null);
    }

    public void requireNotRestricted(UUID userId) {
        String reason = restrictionReason(userId);
        if (reason != null) {
            throw new ForbiddenActionException(reason);
        }
    }

    /** An enabled admin account. */
    public boolean isAdmin(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from users where id = :id and enabled and role = 'ROLE_ADMIN')",
                new MapSqlParameterSource("id", userId), Boolean.class));
    }

    /** The given users who may read the conversation right now; used to route live events. */
    public Set<UUID> readersAmong(ChatConversation conversation, Collection<UUID> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<UUID> readers = new HashSet<>();
        switch (conversation.getType()) {
            case DIRECT -> participantRepository.findByConversationId(conversation.getId()).stream()
                    .map(ChatParticipant::getUserId)
                    .filter(candidates::contains)
                    .forEach(readers::add);
            case COURSE -> readers.addAll(eligibility.eligibleAmong(conversation.getCourseId(), candidates));
            case GROUP -> {
                Set<UUID> members = new HashSet<>();
                participantRepository.findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(conversation.getId()).stream()
                        .map(ChatParticipant::getUserId)
                        .filter(candidates::contains)
                        .forEach(members::add);
                readers.addAll(eligibility.eligibleAmong(conversation.getCourseId(), members));
            }
            case SUPPORT -> {
                if (candidates.contains(conversation.getCreatedBy())) {
                    readers.add(conversation.getCreatedBy());
                }
                readers.addAll(jdbc.queryForList(
                        "select id from users where id in (:ids) and enabled and role = 'ROLE_ADMIN'",
                        new MapSqlParameterSource("ids", candidates), UUID.class));
            }
        }
        return readers;
    }

    private Optional<View> view(Actor actor, ChatConversation conversation) {
        UUID userId = actor.userId();
        ChatParticipant participant = participantRepository.findByConversationIdAndUserId(conversation.getId(), userId)
                .orElse(null);
        return switch (conversation.getType()) {
            case DIRECT -> participant == null ? Optional.empty() : Optional.of(directView(conversation, participant, userId));
            case COURSE -> courseView(conversation, participant, userId);
            case GROUP -> participant == null || !participant.isActive()
                    ? Optional.empty()
                    : courseView(conversation, participant, userId);
            case SUPPORT -> conversation.getCreatedBy() != null && conversation.getCreatedBy().equals(userId) || actor.admin()
                    ? Optional.of(new View(conversation, participant, null, null, firstReason(
                    restrictionReason(userId),
                    conversation.getStatus() == ConversationStatus.OPEN ? null : SUPPORT_CLOSED)))
                    : Optional.empty();
        };
    }

    private View directView(ChatConversation conversation, ChatParticipant participant, UUID userId) {
        UUID peerId = participantRepository.findByConversationId(conversation.getId()).stream()
                .map(ChatParticipant::getUserId)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElse(null);
        String reason = restrictionReason(userId);
        if (reason == null && (peerId == null
                || eligibility.sharedCourseIds(userId, peerId).isEmpty()
                || blockRepository.existsBetween(userId, peerId))) {
            reason = DIRECT_READ_ONLY;
        }
        return new View(conversation, participant, null, peerId, reason);
    }

    private Optional<View> courseView(ChatConversation conversation, ChatParticipant participant, UUID userId) {
        if (!eligibility.isEligible(userId, conversation.getCourseId())) {
            return Optional.empty();
        }
        Course course = courseRepository.findById(conversation.getCourseId()).orElse(null);
        if (course == null) {
            return Optional.empty();
        }
        String reason = firstReason(
                restrictionReason(userId),
                conversation.getType() == ConversationType.GROUP && conversation.getStatus() != ConversationStatus.ACTIVE
                        ? GROUP_ARCHIVED : null,
                course.getStatus() == CourseStatus.PUBLISHED ? null : COURSE_INACTIVE);
        return Optional.of(new View(conversation, participant, course, null, reason));
    }

    private static String firstReason(String... reasons) {
        for (String reason : reasons) {
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    static ResourceNotFoundException notFound(Object id) {
        return ResourceNotFoundException.of("Conversation", id);
    }
}
