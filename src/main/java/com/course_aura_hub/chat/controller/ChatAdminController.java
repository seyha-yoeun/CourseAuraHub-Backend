package com.course_aura_hub.chat.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.auth.security.ClientInfoResolver;
import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.service.ChatModerationService;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin-only chat tools. Responses here may include requester emails. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Chat administration", description = "Support inbox, reported chat messages and chat restrictions")
public class ChatAdminController {

    private final ChatModerationService moderationService;
    private final ClientInfoResolver clientInfoResolver;

    @GetMapping("/api/v1/admin/chat/support")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Support conversations",
            description = "Admin-only. status: open (default) or closed; most recent activity first. Read and answer them "
                    + "with the regular /api/v1/me/chat/conversations/{id} endpoints.")
    public ResponseEntity<ApiResponse<ChatDtos.SupportPage>> support(@RequestParam(defaultValue = "open") String status,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(moderationService.supportThreads(status, page, size)));
    }

    @PostMapping("/api/v1/admin/chat/support/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close a support conversation", description = "Admin-only. It becomes read-only; the user can start a new one.")
    public ResponseEntity<ApiResponse<ChatDtos.Conversation>> closeSupport(@PathVariable Integer id,
                                                                          @AuthenticationPrincipal AuthUserPrincipal admin) {
        return ResponseEntity.ok(ApiResponse.success("Conversation closed", moderationService.closeSupport(Actor.of(admin), id)));
    }

    @GetMapping("/api/v1/admin/chat/reports/{reportId}/context")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reported message in context",
            description = "Admin-only. The reported chat message with ten messages before and after it. Every view is "
                    + "recorded in the admin audit log.")
    public ResponseEntity<ApiResponse<ChatDtos.ReportContext>> reportContext(@PathVariable Integer reportId,
                                                                            @AuthenticationPrincipal AuthUserPrincipal admin,
                                                                            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(moderationService.reportContext(Actor.of(admin), reportId,
                clientInfoResolver.resolveClientIp(request))));
    }

    @PostMapping("/api/v1/admin/chat/restrictions/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restrict someone's chat",
            description = "Admin-only. Until the given time they can read but not send, start conversations or invite.")
    public ResponseEntity<ApiResponse<ChatDtos.Restriction>> restrict(@PathVariable UUID userId,
                                                                     @Valid @RequestBody ChatDtos.RestrictionRequest request,
                                                                     @AuthenticationPrincipal AuthUserPrincipal admin) {
        return ResponseEntity.ok(ApiResponse.success("Chat restricted", moderationService.restrict(Actor.of(admin), userId, request)));
    }

    @DeleteMapping("/api/v1/admin/chat/restrictions/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lift a chat restriction", description = "Admin-only.")
    public ResponseEntity<ApiResponse<Void>> lift(@PathVariable UUID userId, @AuthenticationPrincipal AuthUserPrincipal admin) {
        moderationService.lift(Actor.of(admin), userId);
        return ResponseEntity.ok(ApiResponse.success("Restriction lifted", null));
    }
}
