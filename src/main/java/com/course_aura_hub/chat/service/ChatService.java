package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.common.security.Actor;

import java.util.List;
import java.util.UUID;

/** Conversations, messages, read state, contact settings and blocks. Groups live in {@link ChatGroupService}. */
public interface ChatService {

    ChatDtos.SocketTicket issueSocketTicket(Actor actor);

    /** My conversations, most recent activity first. {@code cursor} is the previous page's {@code nextCursor}. */
    /**
     * My conversations, newest activity first. {@code hidden} lists instead the ones I hid from my list; a
     * conversation hidden that gets a new message is back in the main list.
     */
    ChatDtos.ConversationPage conversations(Actor actor, String cursor, int limit, boolean hidden);

    ChatDtos.UnreadCount unreadCount(Actor actor);

    /**
     * Gets or starts the direct conversation with someone. Starting one needs a shared course, no block either way,
     * and the other person's contact setting to allow it (course staff are always allowed).
     */
    ChatDtos.Conversation openDirect(Actor actor, UUID userId);

    /** Gets my open support conversation, or starts one. */
    ChatDtos.Conversation openSupport(Actor actor);

    /** The official channel of a course I'm an eligible member of; opening it adds it to my list. */
    ChatDtos.Conversation openCourseChannel(Actor actor, String courseSlug);

    ChatDtos.Conversation conversation(Actor actor, Integer conversationId);

    ChatDtos.Conversation updateConversation(Actor actor, Integer conversationId, ChatDtos.ConversationUpdateRequest request);

    /** Messages oldest first: the newest page, or those {@code before} or {@code after} a message id. */
    ChatDtos.MessagePage messages(Actor actor, Integer conversationId, Integer before, Integer after, int limit);

    /** Sends a message. Repeating a {@code clientMessageId} returns the first message instead of a copy. */
    ChatDtos.Message send(Actor actor, Integer conversationId, ChatDtos.SendMessageRequest request);

    /** Moves my read cursor forward (never back) and returns where it is now. */
    int markRead(Actor actor, Integer conversationId, Integer messageId);

    void markDelivered(Actor actor, Integer conversationId, Integer messageId);

    /** Tells the others I'm typing; nothing is stored. */
    void typing(Actor actor, Integer conversationId);

    /** Changes the text of my own message, within the edit window; the earlier text is kept for moderators. */
    ChatDtos.Message editMessage(Actor actor, Integer messageId, ChatDtos.EditMessageRequest request);

    /** Unsends my own message for everyone, within the delete window. */
    ChatDtos.Message deleteMessage(Actor actor, Integer messageId);

    /** Removes a message from my own view only; everyone else still sees it. Works on any message I can read. */
    void deleteForMe(Actor actor, Integer messageId);

    /** Reacts to a message with one of {@link ChatReactions#ALLOWED}, replacing my earlier reaction to it. */
    ChatDtos.Message react(Actor actor, Integer messageId, String emoji);

    /** Takes my reaction off a message, if I had one. */
    ChatDtos.Message unreact(Actor actor, Integer messageId);

    /** Hides a message: group owners and admins in their group, course staff in their course channel. */
    ChatDtos.Message hideMessage(Actor actor, Integer messageId);

    /** Eligible members of a course I'm in, for starting conversations and inviting; leaves out people who blocked me. */
    ChatDtos.MemberPage courseMembers(Actor actor, String courseSlug, String query, String cursor, int limit);

    ChatDtos.Settings settings(Actor actor);

    ChatDtos.Settings updateSettings(Actor actor, ChatDtos.SettingsRequest request);

    List<ChatDtos.BlockedUser> blocks(Actor actor);

    void block(Actor actor, UUID userId);

    void unblock(Actor actor, UUID userId);

    /**
     * Account deletion, in the caller's transaction: leaves every group (handing ownership on), removes the body
     * and files of every message the user sent and shows them as from "Deleted user", ends their memberships,
     * closes their open support conversation, and drops their blocks, settings, restriction and invites.
     */
    void eraseAccount(UUID userId);
}
