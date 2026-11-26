package com.course_aura_hub.chat.controller;

import com.course_aura_hub.chat.service.ChatGroupService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Private groups inside a course. Only members can see a group; course staff and admins have no say in groups
 * they're not part of. People join only by accepting an invite.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Chat groups", description = "Private study groups: create, invite, roles, ownership, leaving")
public class ChatGroupController {

    private final ChatGroupService groupService;

    @PostMapping("/api/v1/me/chat/courses/{slug}/groups")
    @Operation(summary = "Create a group",
            description = "Enrolled learners and course staff of a published course. You become the owner; the people in "
                    + "inviteUserIds get invites (at most 20).")
    public ResponseEntity<ApiResponse<ChatDtos.GroupCreated>> create(@PathVariable String slug,
                                                                     @Valid @RequestBody ChatDtos.CreateGroupRequest request,
                                                                     @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Group created", groupService.create(Actor.of(user), slug, request)));
    }

    @GetMapping("/api/v1/me/chat/courses/{slug}/groups")
    @Operation(summary = "My groups in a course", description = "Enrolled learners and course staff. Only groups you are in.")
    public ResponseEntity<ApiResponse<List<ChatDtos.Conversation>>> myGroups(@PathVariable String slug,
                                                                            @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(groupService.myGroups(Actor.of(user), slug)));
    }

    @PatchMapping("/api/v1/me/chat/groups/{id}")
    @Operation(summary = "Edit a group",
            description = "Group owner and admins. Send the version you last saw; if someone changed the group since, the answer is 409.")
    public ResponseEntity<ApiResponse<ChatDtos.Conversation>> update(@PathVariable Integer id,
                                                                    @Valid @RequestBody ChatDtos.UpdateGroupRequest request,
                                                                    @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Group updated", groupService.update(Actor.of(user), id, request)));
    }

    @DeleteMapping("/api/v1/me/chat/groups/{id}")
    @Operation(summary = "Archive a group", description = "Group owner. Members keep the history, read-only; open invites are revoked.")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable Integer id, @AuthenticationPrincipal AuthUserPrincipal user) {
        groupService.archive(Actor.of(user), id);
        return ResponseEntity.ok(ApiResponse.success("Group archived", null));
    }

    @GetMapping("/api/v1/me/chat/groups/{id}/members")
    @Operation(summary = "Group members", description = "Group members only. Owner first, then admins, then members.")
    public ResponseEntity<ApiResponse<List<ChatDtos.GroupMember>>> members(@PathVariable Integer id,
                                                                          @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(groupService.members(Actor.of(user), id)));
    }

    @PostMapping("/api/v1/me/chat/groups/{id}/invites")
    @Operation(summary = "Invite people",
            description = "Group owner and admins; members too when the group allows it. At most 20 people per call, each with "
                    + "their own result: invited, already_invited, already_member, unavailable or group_full.")
    public ResponseEntity<ApiResponse<ChatDtos.InviteResults>> invite(@PathVariable Integer id,
                                                                     @Valid @RequestBody ChatDtos.InviteRequest request,
                                                                     @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(groupService.invite(Actor.of(user), id, request.userIds())));
    }

    @DeleteMapping("/api/v1/me/chat/groups/{id}/invites/{inviteId}")
    @Operation(summary = "Revoke an invite", description = "Group owner and admins, or the member who sent it.")
    public ResponseEntity<ApiResponse<Void>> revokeInvite(@PathVariable Integer id, @PathVariable Integer inviteId,
                                                          @AuthenticationPrincipal AuthUserPrincipal user) {
        groupService.revokeInvite(Actor.of(user), id, inviteId);
        return ResponseEntity.ok(ApiResponse.success("Invite revoked", null));
    }

    @GetMapping("/api/v1/me/chat/invites")
    @Operation(summary = "My group invites", description = "Any signed-in user. Open invites only.")
    public ResponseEntity<ApiResponse<List<ChatDtos.Invite>>> myInvites(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(groupService.myInvites(Actor.of(user))));
    }

    @PostMapping("/api/v1/me/chat/invites/{inviteId}/accept")
    @Operation(summary = "Accept an invite",
            description = "The invitee. 409 when the invite expired, the group was archived or is full, you left the course, "
                    + "or the inviter can no longer add members.")
    public ResponseEntity<ApiResponse<ChatDtos.Conversation>> accept(@PathVariable Integer inviteId,
                                                                    @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("You joined the group", groupService.accept(Actor.of(user), inviteId)));
    }

    @PostMapping("/api/v1/me/chat/invites/{inviteId}/decline")
    @Operation(summary = "Decline an invite", description = "The invitee.")
    public ResponseEntity<ApiResponse<Void>> decline(@PathVariable Integer inviteId, @AuthenticationPrincipal AuthUserPrincipal user) {
        groupService.decline(Actor.of(user), inviteId);
        return ResponseEntity.ok(ApiResponse.success("Invite declined", null));
    }

    @DeleteMapping("/api/v1/me/chat/groups/{id}/members/{userId}")
    @Operation(summary = "Remove a member", description = "Group owner (anyone) and admins (members only).")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable Integer id, @PathVariable UUID userId,
                                                          @AuthenticationPrincipal AuthUserPrincipal user) {
        groupService.removeMember(Actor.of(user), id, userId);
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }

    @PutMapping("/api/v1/me/chat/groups/{id}/members/{userId}/role")
    @Operation(summary = "Change a member's role", description = "Group owner. role: admin or member.")
    public ResponseEntity<ApiResponse<ChatDtos.GroupMember>> changeRole(@PathVariable Integer id, @PathVariable UUID userId,
                                                                       @Valid @RequestBody ChatDtos.RoleRequest request,
                                                                       @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Role changed", groupService.changeRole(Actor.of(user), id, userId, request.role())));
    }

    @PostMapping("/api/v1/me/chat/groups/{id}/transfer")
    @Operation(summary = "Transfer ownership", description = "Group owner, to another member who is still in the course. You become an admin.")
    public ResponseEntity<ApiResponse<Void>> transfer(@PathVariable Integer id, @Valid @RequestBody ChatDtos.TransferRequest request,
                                                      @AuthenticationPrincipal AuthUserPrincipal user) {
        groupService.transfer(Actor.of(user), id, request.userId());
        return ResponseEntity.ok(ApiResponse.success("Ownership transferred", null));
    }

    @PostMapping("/api/v1/me/chat/groups/{id}/leave")
    @Operation(summary = "Leave a group",
            description = "Group members. An owner who leaves hands the group to the longest-serving admin, then member; "
                    + "the last one out archives it.")
    public ResponseEntity<ApiResponse<Void>> leave(@PathVariable Integer id, @AuthenticationPrincipal AuthUserPrincipal user) {
        groupService.leave(Actor.of(user), id);
        return ResponseEntity.ok(ApiResponse.success("You left the group", null));
    }
}
