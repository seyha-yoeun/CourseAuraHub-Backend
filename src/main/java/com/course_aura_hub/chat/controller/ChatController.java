package com.course_aura_hub.chat.controller;

import com.course_aura_hub.chat.service.ChatService;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Chat for any signed-in user. Conversations someone can't read answer 404, whether or not they exist.
 * Learners see other learners by short names ("Jane L."); emails never appear here.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Direct messages, course channels, support chat, read state, contact settings and blocks")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/api/v1/me/chat/socket-ticket")
    @Operation(summary = "Get a WebSocket ticket",
            description = "Any signed-in user. A single-use ticket, valid 60 seconds, for connecting to the STOMP endpoint "
                    + "/ws/chat?ticket=...; subscribe to /user/queue/chat. Asking again cancels an unused ticket.")
    public ResponseEntity<ApiResponse<ChatDtos.SocketTicket>> socketTicket(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ticket issued", chatService.issueSocketTicket(Actor.of(user))));
    }

    @GetMapping("/api/v1/me/chat/conversations")
    @Operation(summary = "My conversations",
            description = "Any signed-in user. Most recent activity first; pass nextCursor to page. With hidden=true, "
                    + "only the ones I hid from my list that have had no new message since.")
    public ResponseEntity<ApiResponse<ChatDtos.ConversationPage>> conversations(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean hidden,
            @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.conversations(Actor.of(user), cursor, limit, hidden)));
    }

    @GetMapping("/api/v1/me/chat/unread-count")
    @Operation(summary = "Unread chat messages", description = "Any signed-in user. Across all conversations, leaving out muted ones.")
    public ResponseEntity<ApiResponse<ChatDtos.UnreadCount>> unreadCount(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.unreadCount(Actor.of(user))));
    }

    @PostMapping("/api/v1/me/chat/conversations/direct")
    @Operation(summary = "Open a direct conversation",
            description = "Any signed-in user. Returns the existing conversation, or starts one when you share a course, "
                    + "neither of you blocked the other and their contact setting allows it (course staff always may).")
    public ResponseEntity<ApiResponse<ChatDtos.Conversation>> openDirect(@Valid @RequestBody ChatDtos.DirectRequest request,
                                                                        @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.openDirect(Actor.of(user), request.userId())));
    }

    @PostMapping("/api/v1/me/chat/conversations/support")
    @Operation(summary = "Open support chat", description = "Learners and instructors. Returns your open support conversation, or starts one.")
    public ResponseEntity<ApiResponse<ChatDtos.Conversation>> openSupport(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.openSupport(Actor.of(user))));
    }

    @GetMapping("/api/v1/me/chat/courses/{slug}/channel")
    @Operation(summary = "Course channel", description = "Enrolled learners and course staff. Opening it adds it to your conversations.")
    public ResponseEntity<ApiResponse<ChatDtos.Conversation>> courseChannel(@PathVariable String slug,
                                                                           @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.openCourseChannel(Actor.of(user), slug)));
    }

    @GetMapping("/api/v1/me/chat/courses/{slug}/members")
    @Operation(summary = "Course members",
            description = "Enrolled learners and course staff. People you can message or invite, by name; q matches first "
                    + "names (and full names of staff). Leaves out people who blocked you.")
    public ResponseEntity<ApiResponse<ChatDtos.MemberPage>> courseMembers(
            @PathVariable String slug,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.courseMembers(Actor.of(user), slug, q, cursor, limit)));
    }

    @GetMapping("/api/v1/me/chat/conversations/{id}")
    @Operation(summary = "A conversation", description = "Its members only.")
    public ResponseEntity<ApiResponse<ChatDtos.Conversation>> conversation(@PathVariable Integer id,
                                                                          @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.conversation(Actor.of(user), id)));
    }

    @PatchMapping("/api/v1/me/chat/conversations/{id}")
    @Operation(summary = "Mute or hide a conversation",
            description = "Its members only. Muted conversations don't notify; hidden ones leave your list until something new arrives.")
    public ResponseEntity<ApiResponse<ChatDtos.Conversation>> updateConversation(
            @PathVariable Integer id,
            @RequestBody ChatDtos.ConversationUpdateRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Conversation updated", chatService.updateConversation(Actor.of(user), id, request)));
    }

    @GetMapping("/api/v1/me/chat/conversations/{id}/messages")
    @Operation(summary = "Messages", description = "Its members only. Oldest first: the newest page, or those before or after a message id (at most 50).")
    public ResponseEntity<ApiResponse<ChatDtos.MessagePage>> messages(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer before,
            @RequestParam(required = false) Integer after,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.messages(Actor.of(user), id, before, after, limit)));
    }

    @PostMapping("/api/v1/me/chat/conversations/{id}/messages")
    @Operation(summary = "Send a message",
            description = "Its members only. Text up to 4000 characters and up to 5 files uploaded with purpose chat_attachment. "
                    + "Sending the same clientMessageId again returns the first message.")
    public ResponseEntity<ApiResponse<ChatDtos.Message>> send(@PathVariable Integer id,
                                                             @Valid @RequestBody ChatDtos.SendMessageRequest request,
                                                             @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent", chatService.send(Actor.of(user), id, request)));
    }

    @PutMapping("/api/v1/me/chat/conversations/{id}/read")
    @Operation(summary = "Mark as read", description = "Its members only. Moves your read position forward to this message; it never moves back.")
    public ResponseEntity<ApiResponse<Integer>> read(@PathVariable Integer id, @Valid @RequestBody ChatDtos.ReadRequest request,
                                                     @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.markRead(Actor.of(user), id, request.messageId())));
    }

    @DeleteMapping("/api/v1/me/chat/messages/{id}")
    @Operation(summary = "Unsend my message", description = "The sender only, within 15 minutes of sending. It is removed for everyone.")
    public ResponseEntity<ApiResponse<ChatDtos.Message>> deleteMessage(@PathVariable Integer id,
                                                                      @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Message deleted", chatService.deleteMessage(Actor.of(user), id)));
    }

    @DeleteMapping("/api/v1/me/chat/messages/{id}/for-me")
    @Operation(summary = "Delete a message for me",
            description = "Its conversation's members only. Removes any message from your own view; everyone else still sees it.")
    public ResponseEntity<ApiResponse<Void>> deleteForMe(@PathVariable Integer id, @AuthenticationPrincipal AuthUserPrincipal user) {
        chatService.deleteForMe(Actor.of(user), id);
        return ResponseEntity.ok(ApiResponse.success("Message deleted for you", null));
    }

    @PutMapping("/api/v1/me/chat/messages/{id}/reaction")
    @Operation(summary = "React to a message",
            description = "Members who can send in its conversation. One reaction per person: 👍 ❤️ 😂 😮 😢 or 🎉; "
                    + "reacting again with another emoji replaces it.")
    public ResponseEntity<ApiResponse<ChatDtos.Message>> react(@PathVariable Integer id, @Valid @RequestBody ChatDtos.ReactRequest request,
                                                              @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.react(Actor.of(user), id, request.emoji())));
    }

    @DeleteMapping("/api/v1/me/chat/messages/{id}/reaction")
    @Operation(summary = "Remove my reaction", description = "Its conversation's members only.")
    public ResponseEntity<ApiResponse<ChatDtos.Message>> unreact(@PathVariable Integer id,
                                                                @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.unreact(Actor.of(user), id)));
    }

    @PostMapping("/api/v1/me/chat/messages/{id}/hide")
    @Operation(summary = "Hide a message", description = "Group owners and admins in their group; course staff in their course channel.")
    public ResponseEntity<ApiResponse<ChatDtos.Message>> hideMessage(@PathVariable Integer id,
                                                                    @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Message hidden", chatService.hideMessage(Actor.of(user), id)));
    }

    @GetMapping("/api/v1/me/chat/settings")
    @Operation(summary = "My chat settings", description = "Any signed-in user.")
    public ResponseEntity<ApiResponse<ChatDtos.Settings>> settings(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.settings(Actor.of(user))));
    }

    @PutMapping("/api/v1/me/chat/settings")
    @Operation(summary = "Update my chat settings",
            description = "Any signed-in user. directMessages: course_members (default), staff_only or nobody. Staff of a course "
                    + "you share can always message you.")
    public ResponseEntity<ApiResponse<ChatDtos.Settings>> updateSettings(@Valid @RequestBody ChatDtos.SettingsRequest request,
                                                                        @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Settings saved", chatService.updateSettings(Actor.of(user), request)));
    }

    @GetMapping("/api/v1/me/chat/blocks")
    @Operation(summary = "People I blocked", description = "Any signed-in user.")
    public ResponseEntity<ApiResponse<List<ChatDtos.BlockedUser>>> blocks(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(chatService.blocks(Actor.of(user))));
    }

    @PutMapping("/api/v1/me/chat/blocks/{userId}")
    @Operation(summary = "Block someone",
            description = "Any signed-in user. They can no longer message you directly or invite you to groups; they aren't told.")
    public ResponseEntity<ApiResponse<Void>> block(@PathVariable UUID userId, @AuthenticationPrincipal AuthUserPrincipal user) {
        chatService.block(Actor.of(user), userId);
        return ResponseEntity.ok(ApiResponse.success("Blocked", null));
    }

    @DeleteMapping("/api/v1/me/chat/blocks/{userId}")
    @Operation(summary = "Unblock someone", description = "Any signed-in user.")
    public ResponseEntity<ApiResponse<Void>> unblock(@PathVariable UUID userId, @AuthenticationPrincipal AuthUserPrincipal user) {
        chatService.unblock(Actor.of(user), userId);
        return ResponseEntity.ok(ApiResponse.success("Unblocked", null));
    }
}
