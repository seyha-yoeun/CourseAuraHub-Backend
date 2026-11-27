package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.config.ChatProperties;
import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.model.ChatConversation;
import com.course_aura_hub.chat.model.ChatGroupInvite;
import com.course_aura_hub.chat.model.ChatMessage;
import com.course_aura_hub.chat.model.ChatParticipant;
import com.course_aura_hub.chat.model.ConversationStatus;
import com.course_aura_hub.chat.model.ConversationType;
import com.course_aura_hub.chat.model.InviteStatus;
import com.course_aura_hub.chat.model.LeftReason;
import com.course_aura_hub.chat.model.MessageKind;
import com.course_aura_hub.chat.model.ParticipantRole;
import com.course_aura_hub.chat.repository.ChatBlockRepository;
import com.course_aura_hub.chat.repository.ChatConversationRepository;
import com.course_aura_hub.chat.repository.ChatGroupInviteRepository;
import com.course_aura_hub.chat.repository.ChatMessageRepository;
import com.course_aura_hub.chat.repository.ChatParticipantRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.DuplicateResourceException;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.model.CourseStatus;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.notification.model.NotificationType;
import com.course_aura_hub.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ChatGroupServiceImpl implements ChatGroupService {

    private static final int MAX_INVITES_PER_CALL = 20;

    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatGroupInviteRepository inviteRepository;
    private final ChatBlockRepository blockRepository;
    private final CourseRepository courseRepository;
    private final ChatAccess access;
    private final ChatEligibility eligibility;
    private final ChatPeople people;
    private final ChatViews views;
    private final ChatRateLimiter rateLimiter;
    private final ChatProperties properties;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public ChatGroupServiceImpl(
            ChatConversationRepository conversationRepository,
            ChatParticipantRepository participantRepository,
            ChatMessageRepository messageRepository,
            ChatGroupInviteRepository inviteRepository,
            ChatBlockRepository blockRepository,
            CourseRepository courseRepository,
            ChatAccess access,
            ChatEligibility eligibility,
            ChatPeople people,
            ChatViews views,
            ChatRateLimiter rateLimiter,
            ChatProperties properties,
            NotificationService notificationService,
            ApplicationEventPublisher events,
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.inviteRepository = inviteRepository;
        this.blockRepository = blockRepository;
        this.courseRepository = courseRepository;
        this.access = access;
        this.eligibility = eligibility;
        this.people = people;
        this.views = views;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.notificationService = notificationService;
        this.events = events;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** A group locked for a membership change, with the caller's active membership. */
    private record Locked(ChatConversation group, ChatParticipant me) {
    }

    @Override
    @Transactional
    public ChatDtos.GroupCreated create(Actor actor, String courseSlug, ChatDtos.CreateGroupRequest request) {
        UUID me = actor.userId();
        Course course = courseRepository.findBySlug(courseSlug).orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        if (!eligibility.isEligible(me, course.getId())) {
            throw ResourceNotFoundException.of("Course", courseSlug);
        }
        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleException("Groups can only be created in published courses");
        }
        access.requireNotRestricted(me);
        if (rateLimiter.enabled() && countSince("select count(*) from chat_conversations where type = 'GROUP' and created_by = :me "
                + "and created_at > :since", me) >= properties.groupsPerDay()) {
            throw new BusinessRuleException("You've created a lot of groups today. Try again tomorrow.");
        }
        List<UUID> inviteIds = request.inviteUserIds() == null ? List.of() : request.inviteUserIds();
        if (inviteIds.size() > MAX_INVITES_PER_CALL) {
            throw new BusinessRuleException("Invite at most 20 people at a time");
        }
        ChatConversation group = conversationRepository.save(ChatConversation.builder()
                .type(ConversationType.GROUP)
                .courseId(course.getId())
                .title(request.title().strip())
                .description(StringUtils.hasText(request.description()) ? request.description().strip() : null)
                .ownerId(me)
                .allowMemberInvites(Boolean.TRUE.equals(request.allowMemberInvites()))
                .status(ConversationStatus.ACTIVE)
                .createdBy(me)
                .build());
        participantRepository.saveAndFlush(ChatParticipant.builder()
                .conversationId(group.getId())
                .userId(me)
                .role(ParticipantRole.OWNER)
                .build());
        systemLine(group, people.publicName(me) + " created the group");
        List<ChatDtos.InviteResult> results = inviteIds.isEmpty()
                ? List.of() : sendInvites(group, participantRepository.findByConversationIdAndUserId(group.getId(), me).orElseThrow(), inviteIds);
        log.info("Chat group {} created in course {} by {}", group.getId(), course.getId(), me);
        return new ChatDtos.GroupCreated(views.conversation(me, actor.admin(), access.requireReadable(actor, group)), results);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatDtos.Conversation> myGroups(Actor actor, String courseSlug) {
        Course course = courseRepository.findBySlug(courseSlug).orElseThrow(() -> ResourceNotFoundException.of("Course", courseSlug));
        if (!eligibility.isEligible(actor.userId(), course.getId())) {
            throw ResourceNotFoundException.of("Course", courseSlug);
        }
        List<Integer> ids = jdbc.queryForList("""
                select cv.id from chat_conversations cv
                join chat_participants p on p.conversation_id = cv.id and p.user_id = :me and p.left_at is null
                where cv.type = 'GROUP' and cv.course_id = :courseId
                order by cv.last_activity_at desc, cv.id desc
                """, new MapSqlParameterSource("me", actor.userId()).addValue("courseId", course.getId()), Integer.class);
        List<ChatDtos.Conversation> groups = new ArrayList<>();
        for (Integer id : ids) {
            conversationRepository.findById(id).ifPresent(group ->
                    groups.add(views.conversation(actor.userId(), actor.admin(), access.requireReadable(actor, group))));
        }
        return groups;
    }

    @Override
    @Transactional
    public ChatDtos.Conversation update(Actor actor, Integer groupId, ChatDtos.UpdateGroupRequest request) {
        Locked locked = lock(actor, groupId);
        ChatConversation group = locked.group();
        requireRole(locked.me(), "Only the owner and admins can edit the group", ParticipantRole.OWNER, ParticipantRole.ADMIN);
        requireActive(group);
        if (request.version() == null || request.version() != group.getVersion()) {
            throw new DuplicateResourceException("Someone else changed this group. Reload it and try again.");
        }
        String oldTitle = group.getTitle();
        if (request.title() != null) {
            if (!StringUtils.hasText(request.title())) {
                throw new BusinessRuleException("Give the group a name");
            }
            group.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            group.setDescription(StringUtils.hasText(request.description()) ? request.description().strip() : null);
        }
        if (request.allowMemberInvites() != null) {
            group.setAllowMemberInvites(request.allowMemberInvites());
        }
        group.setVersion(group.getVersion() + 1);
        conversationRepository.save(group);
        if (!Objects.equals(oldTitle, group.getTitle())) {
            systemLine(group, people.publicName(actor.userId()) + " renamed the group to \"" + group.getTitle() + "\"");
        }
        events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.CONVERSATION_UPDATED, groupId, actor.userId(), null));
        return views.conversation(actor.userId(), actor.admin(), access.requireReadable(actor, group));
    }

    @Override
    @Transactional
    public void archive(Actor actor, Integer groupId) {
        Locked locked = lock(actor, groupId);
        requireRole(locked.me(), "Only the owner can archive the group", ParticipantRole.OWNER);
        requireActive(locked.group());
        archiveLocked(locked.group(), people.publicName(actor.userId()) + " archived the group", actor.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatDtos.GroupMember> members(Actor actor, Integer groupId) {
        ChatAccess.View view = access.requireReadable(actor, groupId);
        if (view.conversation().getType() != ConversationType.GROUP) {
            throw ChatAccess.notFound(groupId);
        }
        List<ChatParticipant> active = participantRepository.findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(groupId);
        Map<UUID, ChatDtos.Person> named = people.lookup(actor.userId(), active.stream().map(ChatParticipant::getUserId).toList());
        return active.stream()
                .filter(member -> named.containsKey(member.getUserId()))
                .sorted(Comparator.comparing((ChatParticipant member) -> member.getRole().ordinal())
                        .thenComparing(ChatParticipant::getJoinedAt))
                .map(member -> new ChatDtos.GroupMember(named.get(member.getUserId()), ChatViews.lower(member.getRole()),
                        member.getJoinedAt()))
                .toList();
    }

    @Override
    @Transactional
    public ChatDtos.InviteResults invite(Actor actor, Integer groupId, List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessRuleException("Choose who to invite");
        }
        if (userIds.size() > MAX_INVITES_PER_CALL) {
            throw new BusinessRuleException("Invite at most 20 people at a time");
        }
        Locked locked = lock(actor, groupId);
        requireActive(locked.group());
        return new ChatDtos.InviteResults(sendInvites(locked.group(), locked.me(), userIds));
    }

    @Override
    @Transactional
    public void revokeInvite(Actor actor, Integer groupId, Integer inviteId) {
        Locked locked = lock(actor, groupId);
        ChatGroupInvite invite = inviteRepository.findById(inviteId)
                .filter(found -> found.getConversationId().equals(groupId) && found.getStatus() == InviteStatus.PENDING)
                .orElseThrow(() -> ResourceNotFoundException.of("Invite", inviteId));
        boolean manager = locked.me().getRole() == ParticipantRole.OWNER || locked.me().getRole() == ParticipantRole.ADMIN;
        if (!manager && !actor.userId().equals(invite.getInviterId())) {
            throw new ForbiddenActionException("You can only revoke invites you sent");
        }
        closeInvite(invite, InviteStatus.REVOKED);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatDtos.Invite> myInvites(Actor actor) {
        List<ChatGroupInvite> invites = inviteRepository
                .findByInviteeIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(actor.userId(), InviteStatus.PENDING, Instant.now());
        List<ChatDtos.Invite> views = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        invites.forEach(invite -> {
            ids.add(invite.getInviterId());
            ids.add(invite.getInviteeId());
        });
        Map<UUID, ChatDtos.Person> named = people.lookup(actor.userId(), ids);
        for (ChatGroupInvite invite : invites) {
            ChatConversation group = conversationRepository.findById(invite.getConversationId()).orElse(null);
            if (group == null || group.getStatus() != ConversationStatus.ACTIVE) {
                continue;
            }
            views.add(inviteView(invite, group, named));
        }
        return views;
    }

    @Override
    @Transactional
    public ChatDtos.Conversation accept(Actor actor, Integer inviteId) {
        UUID me = actor.userId();
        ChatConversation group = lockInviteGroup(inviteId, me);
        sweepIneligible(group);
        // Read under the group lock: a revoke or a second accept may have committed meanwhile
        ChatGroupInvite current = inviteRepository.findById(inviteId).orElseThrow(() -> ResourceNotFoundException.of("Invite", inviteId));
        if (current.getStatus() != InviteStatus.PENDING) {
            throw new DuplicateResourceException("This invite is no longer open");
        }
        if (!current.getExpiresAt().isAfter(Instant.now())) {
            throw new DuplicateResourceException("This invite has expired");
        }
        if (group.getStatus() != ConversationStatus.ACTIVE) {
            throw new DuplicateResourceException("This group was archived");
        }
        if (!eligibility.isEligible(me, group.getCourseId())) {
            throw new DuplicateResourceException("You're no longer a member of this course");
        }
        ChatParticipant inviter = current.getInviterId() == null ? null
                : participantRepository.findByConversationIdAndUserId(group.getId(), current.getInviterId()).orElse(null);
        if (inviter == null || !inviter.isActive() || !canInvite(group, inviter)) {
            throw new DuplicateResourceException("The person who invited you can no longer add members");
        }
        if (participantRepository.countByConversationIdAndLeftAtIsNull(group.getId()) >= properties.groupMemberLimit()) {
            throw new DuplicateResourceException("This group is full");
        }
        int cursor = group.getLastMessageId() == null ? 0 : group.getLastMessageId();
        ChatParticipant membership = participantRepository.findByConversationIdAndUserId(group.getId(), me).orElse(null);
        if (membership != null && membership.isActive()) {
            current.setStatus(InviteStatus.ACCEPTED);
            current.setRespondedAt(Instant.now());
            return views.conversation(me, actor.admin(), access.requireReadable(actor, group));
        }
        if (membership == null) {
            membership = ChatParticipant.builder().conversationId(group.getId()).userId(me).build();
        }
        membership.setRole(ParticipantRole.MEMBER);
        membership.setJoinedAt(Instant.now());
        membership.setLeftAt(null);
        membership.setLeftReason(null);
        membership.setRemovedBy(null);
        membership.setMuted(false);
        membership.setHiddenAt(null);
        membership.setLastReadMessageId(Math.max(membership.getLastReadMessageId(), cursor));
        membership.setLastDeliveredMessageId(Math.max(membership.getLastDeliveredMessageId(), cursor));
        participantRepository.saveAndFlush(membership);
        current.setStatus(InviteStatus.ACCEPTED);
        current.setRespondedAt(Instant.now());
        inviteRepository.save(current);
        systemLine(group, people.publicName(me) + " joined");
        events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.MEMBER_JOINED, group.getId(), me, me));
        events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.CONVERSATION_CREATED, group.getId(), me, me));
        events.publishEvent(ChatSignal.invite(ChatSignal.Kind.INVITE_REVOKED, group.getId(), inviteId, me));
        log.info("User {} joined chat group {}", me, group.getId());
        return views.conversation(me, actor.admin(), access.requireReadable(actor, group));
    }

    @Override
    @Transactional
    public void decline(Actor actor, Integer inviteId) {
        lockInviteGroup(inviteId, actor.userId());
        ChatGroupInvite invite = inviteRepository.findById(inviteId)
                .filter(found -> found.getStatus() == InviteStatus.PENDING)
                .orElseThrow(() -> ResourceNotFoundException.of("Invite", inviteId));
        closeInvite(invite, InviteStatus.DECLINED);
    }

    @Override
    @Transactional
    public void removeMember(Actor actor, Integer groupId, UUID userId) {
        if (actor.userId().equals(userId)) {
            throw new BusinessRuleException("Leave the group instead of removing yourself");
        }
        Locked locked = lock(actor, groupId);
        ChatParticipant target = activeMember(groupId, userId);
        ParticipantRole myRole = locked.me().getRole();
        boolean allowed = myRole == ParticipantRole.OWNER
                || myRole == ParticipantRole.ADMIN && target.getRole() == ParticipantRole.MEMBER;
        if (!allowed) {
            throw new ForbiddenActionException(myRole == ParticipantRole.ADMIN
                    ? "Admins can only remove members" : "Only the owner and admins can remove people");
        }
        markLeft(target, LeftReason.REMOVED, actor.userId());
        systemLine(locked.group(), people.publicName(actor.userId()) + " removed " + people.publicName(userId));
        log.info("User {} removed {} from chat group {}", actor.userId(), userId, groupId);
    }

    @Override
    @Transactional
    public ChatDtos.GroupMember changeRole(Actor actor, Integer groupId, UUID userId, String role) {
        ParticipantRole newRole = parseRole(role);
        Locked locked = lock(actor, groupId);
        requireRole(locked.me(), "Only the owner can change roles", ParticipantRole.OWNER);
        ChatParticipant target = activeMember(groupId, userId);
        if (target.getRole() == ParticipantRole.OWNER) {
            throw new BusinessRuleException("Transfer ownership to change the owner");
        }
        if (target.getRole() != newRole) {
            target.setRole(newRole);
            participantRepository.save(target);
            systemLine(locked.group(), newRole == ParticipantRole.ADMIN
                    ? people.publicName(actor.userId()) + " made " + people.publicName(userId) + " an admin"
                    : people.publicName(userId) + " is no longer an admin");
            events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.MEMBER_ROLE, groupId, actor.userId(), userId));
        }
        return new ChatDtos.GroupMember(people.one(actor.userId(), userId), ChatViews.lower(newRole), target.getJoinedAt());
    }

    @Override
    @Transactional
    public void transfer(Actor actor, Integer groupId, UUID userId) {
        Locked locked = lock(actor, groupId);
        requireRole(locked.me(), "Only the owner can transfer ownership", ParticipantRole.OWNER);
        requireActive(locked.group());
        if (actor.userId().equals(userId)) {
            throw new BusinessRuleException("You already own this group");
        }
        ChatParticipant target = activeMember(groupId, userId);
        if (!eligibility.isEligible(userId, locked.group().getCourseId())) {
            throw new BusinessRuleException("This person is no longer in the course");
        }
        // Demote first: the database allows only one active owner per group
        locked.me().setRole(ParticipantRole.ADMIN);
        participantRepository.saveAndFlush(locked.me());
        promote(locked.group(), target);
        systemLine(locked.group(), people.publicName(actor.userId()) + " made " + people.publicName(userId) + " the owner");
        events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.MEMBER_ROLE, groupId, actor.userId(), actor.userId()));
        log.info("Chat group {} transferred from {} to {}", groupId, actor.userId(), userId);
    }

    @Override
    @Transactional
    public void leave(Actor actor, Integer groupId) {
        Locked locked = lock(actor, groupId);
        leaveLocked(locked.group(), locked.me(), people.publicName(actor.userId()) + " left");
    }

    @Override
    public int runMaintenance() {
        Integer expired = transactions.execute(status -> inviteRepository.expireDue(Instant.now()));
        List<Integer> candidates = jdbc.queryForList("""
                select distinct cv.id
                from chat_conversations cv
                join courses c on c.id = cv.course_id
                join chat_participants p on p.conversation_id = cv.id and p.left_at is null
                join users u on u.id = p.user_id
                where cv.type = 'GROUP' and cv.status = 'ACTIVE'
                  and (c.status = 'ARCHIVED' or not %s)
                order by cv.id
                """.formatted(ChatEligibility.ELIGIBLE_SQL), new MapSqlParameterSource(), Integer.class);
        int changed = 0;
        for (Integer groupId : candidates) {
            Boolean done = transactions.execute(status -> maintainGroup(groupId));
            if (Boolean.TRUE.equals(done)) {
                changed++;
            }
        }
        log.info("Chat maintenance: {} invites expired, {} groups updated", expired, changed);
        return changed;
    }

    @Override
    @Transactional
    public void leaveAllForDeletedAccount(UUID userId) {
        List<Integer> groupIds = jdbc.queryForList("""
                select p.conversation_id from chat_participants p
                join chat_conversations cv on cv.id = p.conversation_id
                where p.user_id = :userId and p.left_at is null and cv.type = 'GROUP'
                order by p.conversation_id
                """, new MapSqlParameterSource("userId", userId), Integer.class);
        for (Integer groupId : groupIds) {
            ChatConversation group = conversationRepository.findByIdForUpdate(groupId).orElse(null);
            ChatParticipant member = participantRepository.findByConversationIdAndUserId(groupId, userId).orElse(null);
            if (group != null && member != null && member.isActive()) {
                leaveLocked(group, member, ChatPeople.DELETED_NAME + " left");
            }
        }
        for (ChatGroupInvite invite : pendingInvitesSentBy(userId)) {
            closeInvite(invite, InviteStatus.REVOKED);
        }
        jdbc.update("delete from chat_group_invites where invitee_id = :userId", new MapSqlParameterSource("userId", userId));
    }

    // ---- membership internals (callers hold the group lock) ----

    private Locked lock(Actor actor, Integer groupId) {
        ChatConversation group = conversationRepository.findByIdForUpdate(groupId)
                .filter(found -> found.getType() == ConversationType.GROUP)
                .orElseThrow(() -> ChatAccess.notFound(groupId));
        sweepIneligible(group);
        ChatParticipant me = participantRepository.findByConversationIdAndUserId(groupId, actor.userId())
                .filter(ChatParticipant::isActive)
                .orElseThrow(() -> ChatAccess.notFound(groupId));
        return new Locked(group, me);
    }

    /** Locks the group of one of my invites before the invite itself is read, so responses and revokes serialize. */
    private ChatConversation lockInviteGroup(Integer inviteId, UUID inviteeId) {
        List<Integer> groupIds = jdbc.queryForList(
                "select conversation_id from chat_group_invites where id = :id and invitee_id = :me",
                new MapSqlParameterSource("id", inviteId).addValue("me", inviteeId), Integer.class);
        if (groupIds.isEmpty()) {
            throw ResourceNotFoundException.of("Invite", inviteId);
        }
        return conversationRepository.findByIdForUpdate(groupIds.getFirst())
                .orElseThrow(() -> ResourceNotFoundException.of("Invite", inviteId));
    }

    private boolean maintainGroup(Integer groupId) {
        List<Integer> lockedIds = jdbc.queryForList(
                "select id from chat_conversations where id = :id and status = 'ACTIVE' for update skip locked",
                new MapSqlParameterSource("id", groupId), Integer.class);
        if (lockedIds.isEmpty()) {
            return false;
        }
        ChatConversation group = conversationRepository.findById(groupId).orElse(null);
        if (group == null) {
            return false;
        }
        Course course = courseRepository.findById(group.getCourseId()).orElse(null);
        if (course == null || course.getStatus() == CourseStatus.ARCHIVED) {
            archiveLocked(group, "This group was archived because the course ended", null);
            return true;
        }
        return sweepIneligible(group);
    }

    /** Takes out members who lost access to the course; returns whether anyone was taken out. */
    private boolean sweepIneligible(ChatConversation group) {
        if (group.getStatus() != ConversationStatus.ACTIVE) {
            return false;
        }
        List<ChatParticipant> active = participantRepository.findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(group.getId());
        Set<UUID> eligible = eligibility.eligibleAmong(group.getCourseId(), active.stream().map(ChatParticipant::getUserId).toList());
        boolean ownerGone = false;
        boolean changed = false;
        for (ChatParticipant member : active) {
            if (eligible.contains(member.getUserId())) {
                continue;
            }
            ownerGone |= member.getRole() == ParticipantRole.OWNER;
            markLeft(member, LeftReason.INELIGIBLE, null);
            systemLine(group, people.publicName(member.getUserId()) + " is no longer in this course");
            changed = true;
        }
        if (changed) {
            handOver(group, ownerGone);
        }
        return changed;
    }

    private void leaveLocked(ChatConversation group, ChatParticipant member, String line) {
        boolean wasOwner = member.getRole() == ParticipantRole.OWNER;
        markLeft(member, LeftReason.LEFT, null);
        if (group.getStatus() == ConversationStatus.ACTIVE) {
            systemLine(group, line);
            handOver(group, wasOwner);
        }
    }

    /** After someone left: archive an empty group, or give a group without an owner a new one. */
    private void handOver(ChatConversation group, boolean ownerGone) {
        List<ChatParticipant> remaining = participantRepository.findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(group.getId());
        Set<UUID> eligible = eligibility.eligibleAmong(group.getCourseId(), remaining.stream().map(ChatParticipant::getUserId).toList());
        List<ChatParticipant> candidates = remaining.stream().filter(member -> eligible.contains(member.getUserId())).toList();
        if (candidates.isEmpty()) {
            archiveLocked(group, "Everyone left, so the group was archived", null);
            return;
        }
        if (!ownerGone) {
            return;
        }
        ChatParticipant successor = candidates.stream()
                .filter(member -> member.getRole() == ParticipantRole.ADMIN)
                .min(Comparator.comparing(ChatParticipant::getJoinedAt))
                .orElseGet(() -> candidates.stream().min(Comparator.comparing(ChatParticipant::getJoinedAt)).orElseThrow());
        promote(group, successor);
        systemLine(group, people.publicName(successor.getUserId()) + " is now the owner");
        events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.MEMBER_ROLE, group.getId(), null, successor.getUserId()));
    }

    private void promote(ChatConversation group, ChatParticipant member) {
        member.setRole(ParticipantRole.OWNER);
        participantRepository.saveAndFlush(member);
        group.setOwnerId(member.getUserId());
        conversationRepository.save(group);
    }

    private void markLeft(ChatParticipant member, LeftReason reason, UUID removedBy) {
        member.setLeftAt(Instant.now());
        member.setLeftReason(reason);
        member.setRemovedBy(removedBy);
        if (member.getRole() == ParticipantRole.OWNER) {
            // A former owner keeps no role; the owner index only counts active rows, but keep the history plain
            member.setRole(ParticipantRole.MEMBER);
        }
        participantRepository.saveAndFlush(member);
        events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.MEMBER_LEFT, member.getConversationId(), removedBy, member.getUserId()));
        events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.ACCESS_LOST, member.getConversationId(), removedBy, member.getUserId()));
    }

    private void archiveLocked(ChatConversation group, String line, UUID actorId) {
        systemLine(group, line);
        group.setStatus(ConversationStatus.ARCHIVED);
        group.setVersion(group.getVersion() + 1);
        List<ChatParticipant> remaining = participantRepository.findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(group.getId());
        if (remaining.isEmpty()) {
            group.setOwnerId(null);
        }
        conversationRepository.save(group);
        inviteRepository.findByConversationIdAndStatus(group.getId(), InviteStatus.PENDING)
                .forEach(invite -> closeInvite(invite, InviteStatus.REVOKED));
        events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.CONVERSATION_UPDATED, group.getId(), actorId, null));
        log.info("Chat group {} archived", group.getId());
    }

    private List<ChatDtos.InviteResult> sendInvites(ChatConversation group, ChatParticipant inviter, List<UUID> userIds) {
        UUID me = inviter.getUserId();
        if (!canInvite(group, inviter)) {
            throw new ForbiddenActionException("Only the owner and admins can invite people to this group");
        }
        access.requireNotRestricted(me);
        List<UUID> unique = List.copyOf(new LinkedHashSet<>(userIds));
        if (rateLimiter.enabled() && inviteRepository.countByInviterIdAndCreatedAtAfter(me, Instant.now().minus(Duration.ofDays(1)))
                + unique.size() > properties.invitesPerDay()) {
            throw new BusinessRuleException("You've sent a lot of invites today. Try again tomorrow.");
        }
        Set<UUID> eligible = eligibility.eligibleAmong(group.getCourseId(), unique);
        long members = participantRepository.countByConversationIdAndLeftAtIsNull(group.getId());
        String inviterName = people.publicName(me);
        List<ChatDtos.InviteResult> results = new ArrayList<>();
        for (UUID userId : unique) {
            Optional<ChatParticipant> existing = participantRepository.findByConversationIdAndUserId(group.getId(), userId);
            if (existing.isPresent() && existing.get().isActive()) {
                results.add(new ChatDtos.InviteResult(userId, "already_member", null));
                continue;
            }
            // Ineligible and blocked look the same, so an invite never reveals a block
            if (!eligible.contains(userId) || blockRepository.existsByBlockerIdAndBlockedId(userId, me)) {
                results.add(new ChatDtos.InviteResult(userId, "unavailable", null));
                continue;
            }
            if (members >= properties.groupMemberLimit()) {
                results.add(new ChatDtos.InviteResult(userId, "group_full", null));
                continue;
            }
            Optional<ChatGroupInvite> pending = inviteRepository
                    .findByConversationIdAndInviteeIdAndStatus(group.getId(), userId, InviteStatus.PENDING);
            if (pending.isPresent() && pending.get().getExpiresAt().isAfter(Instant.now())) {
                results.add(new ChatDtos.InviteResult(userId, "already_invited", pending.get().getId()));
                continue;
            }
            pending.ifPresent(stale -> {
                stale.setStatus(InviteStatus.EXPIRED);
                stale.setRespondedAt(Instant.now());
                inviteRepository.saveAndFlush(stale);
            });
            ChatGroupInvite invite = inviteRepository.save(ChatGroupInvite.builder()
                    .conversationId(group.getId())
                    .inviteeId(userId)
                    .inviterId(me)
                    .status(InviteStatus.PENDING)
                    .expiresAt(Instant.now().plus(properties.inviteTtl()))
                    .build());
            notificationService.notifyUsersOnce(List.of(userId), NotificationType.CHAT_MESSAGE,
                    "Group invite", inviterName + " invited you to join \"" + group.getTitle() + "\"", "/chat/invites");
            events.publishEvent(ChatSignal.invite(ChatSignal.Kind.INVITE_RECEIVED, group.getId(), invite.getId(), userId));
            results.add(new ChatDtos.InviteResult(userId, "invited", invite.getId()));
        }
        return results;
    }

    private boolean canInvite(ChatConversation group, ChatParticipant member) {
        return member.getRole() == ParticipantRole.OWNER || member.getRole() == ParticipantRole.ADMIN
                || group.isAllowMemberInvites();
    }

    private void closeInvite(ChatGroupInvite invite, InviteStatus status) {
        invite.setStatus(status);
        invite.setRespondedAt(Instant.now());
        inviteRepository.save(invite);
        events.publishEvent(ChatSignal.invite(ChatSignal.Kind.INVITE_REVOKED, invite.getConversationId(), invite.getId(),
                invite.getInviteeId()));
    }

    private List<ChatGroupInvite> pendingInvitesSentBy(UUID userId) {
        List<Integer> ids = jdbc.queryForList(
                "select id from chat_group_invites where inviter_id = :userId and status = 'PENDING'",
                new MapSqlParameterSource("userId", userId), Integer.class);
        return inviteRepository.findAllById(ids);
    }

    private ChatParticipant activeMember(Integer groupId, UUID userId) {
        return participantRepository.findByConversationIdAndUserId(groupId, userId)
                .filter(ChatParticipant::isActive)
                .orElseThrow(() -> ResourceNotFoundException.of("Member", userId));
    }

    private void systemLine(ChatConversation group, String text) {
        ChatMessage line = messageRepository.saveAndFlush(ChatMessage.builder()
                .conversationId(group.getId())
                .kind(MessageKind.SYSTEM)
                .body(text)
                .build());
        // The group row is locked (or new), so moving the pointer on the entity can't lose a concurrent send
        if (group.getLastMessageId() == null || group.getLastMessageId() < line.getId()) {
            group.setLastMessageId(line.getId());
            group.setLastActivityAt(line.getCreatedAt());
            conversationRepository.save(group);
        }
        events.publishEvent(ChatSignal.message(ChatSignal.Kind.MESSAGE_CREATED, group.getId(), line.getId(), null));
    }

    private ChatDtos.Invite inviteView(ChatGroupInvite invite, ChatConversation group, Map<UUID, ChatDtos.Person> named) {
        Course course = courseRepository.findById(group.getCourseId()).orElse(null);
        return new ChatDtos.Invite(invite.getId(), group.getId(), group.getTitle(),
                course == null ? null : new ChatDtos.CourseRef(course.getId(), course.getSlug(), course.getTitle()),
                named.get(invite.getInviterId()), named.get(invite.getInviteeId()), ChatViews.lower(invite.getStatus()),
                invite.getCreatedAt(), invite.getExpiresAt());
    }

    private static void requireRole(ChatParticipant member, String message, ParticipantRole... roles) {
        for (ParticipantRole role : roles) {
            if (member.getRole() == role) {
                return;
            }
        }
        throw new ForbiddenActionException(message);
    }

    private static void requireActive(ChatConversation group) {
        if (group.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessRuleException("This group was archived");
        }
    }

    private static ParticipantRole parseRole(String role) {
        String value = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(value)) {
            return ParticipantRole.ADMIN;
        }
        if ("MEMBER".equals(value)) {
            return ParticipantRole.MEMBER;
        }
        throw new BusinessRuleException("Choose admin or member");
    }

    private long countSince(String sql, UUID userId) {
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("me", userId)
                .addValue("since", Timestamp.from(Instant.now().minus(Duration.ofDays(1)))), Long.class);
        return count == null ? 0 : count;
    }
}
