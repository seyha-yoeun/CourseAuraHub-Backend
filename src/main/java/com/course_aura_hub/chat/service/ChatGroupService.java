package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.common.security.Actor;

import java.util.List;
import java.util.UUID;

/**
 * Private groups inside a course. Every membership change locks the group's conversation row first, so checks
 * (roles, the member cap, eligibility) and changes for one group never interleave. A group always has exactly one
 * owner while it is active; when the owner leaves or loses access, ownership passes to the longest-serving admin,
 * then the longest-serving member, and a group nobody is left in is archived.
 */
public interface ChatGroupService {

    ChatDtos.GroupCreated create(Actor actor, String courseSlug, ChatDtos.CreateGroupRequest request);

    /** My groups in this course; never anyone else's. */
    List<ChatDtos.Conversation> myGroups(Actor actor, String courseSlug);

    ChatDtos.Conversation update(Actor actor, Integer groupId, ChatDtos.UpdateGroupRequest request);

    void archive(Actor actor, Integer groupId);

    List<ChatDtos.GroupMember> members(Actor actor, Integer groupId);

    /** Invites eligible members of the group's course; each person gets their own result (partial success). */
    ChatDtos.InviteResults invite(Actor actor, Integer groupId, List<UUID> userIds);

    void revokeInvite(Actor actor, Integer groupId, Integer inviteId);

    List<ChatDtos.Invite> myInvites(Actor actor);

    /** Joins the group, re-checking the invite, the group, my eligibility, the inviter's rights and the member cap. */
    ChatDtos.Conversation accept(Actor actor, Integer inviteId);

    void decline(Actor actor, Integer inviteId);

    void removeMember(Actor actor, Integer groupId, UUID userId);

    ChatDtos.GroupMember changeRole(Actor actor, Integer groupId, UUID userId, String role);

    void transfer(Actor actor, Integer groupId, UUID userId);

    void leave(Actor actor, Integer groupId);

    /**
     * The hourly clean-up: expires old invites, takes members who lost access to the course out of their groups
     * (handing ownership on), and archives groups of archived courses. Returns how many groups it changed.
     */
    int runMaintenance();

    /** Account deletion: leaves every group (handing ownership on), revokes sent invites, drops received ones. */
    void leaveAllForDeletedAccount(UUID userId);
}
