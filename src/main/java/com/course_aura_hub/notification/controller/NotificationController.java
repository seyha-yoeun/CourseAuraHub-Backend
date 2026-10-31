package com.course_aura_hub.notification.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.notification.dto.NotificationPageResponse;
import com.course_aura_hub.notification.dto.NotificationResponse;
import com.course_aura_hub.notification.dto.UnreadCountResponse;
import com.course_aura_hub.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notifications", description = "The caller's notifications and live stream")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List my notifications", description = "Newest first.")
    public ResponseEntity<ApiResponse<NotificationPageResponse>> findMine(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.findMine(user.getUserId(), unreadOnly, page, size)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count my unread notifications")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> countUnread(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(new UnreadCountResponse(notificationService.countUnread(user.getUserId()))));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markRead(user.getUserId(), id)));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> markAllRead(@AuthenticationPrincipal AuthUserPrincipal user) {
        notificationService.markAllRead(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(new UnreadCountResponse(0)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        notificationService.delete(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Notification deleted", null));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Live notification stream",
            description = "Server-Sent Events. Sends unread-count on connect, then notification events. "
                    + "Closes after the configured timeout so reconnecting re-checks the session."
    )
    public SseEmitter stream(
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            @AuthenticationPrincipal AuthUserPrincipal user,
            HttpServletResponse response
    ) {
        // Stops reverse proxies from buffering events
        response.setHeader("X-Accel-Buffering", "no");
        return notificationService.openStream(user.getUserId(), lastEventId);
    }
}
