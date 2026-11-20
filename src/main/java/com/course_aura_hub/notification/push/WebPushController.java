package com.course_aura_hub.notification.push;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Browser push", description = "Web Push subscriptions and which notifications are pushed")
public class WebPushController {

    private final WebPushService pushService;

    @GetMapping("/api/v1/push/vapid-public-key")
    @Operation(summary = "VAPID public key", description = "Pass it as applicationServerKey when subscribing. "
            + "enabled is false when the server has no push keys.")
    public ResponseEntity<ApiResponse<PushDtos.VapidKeyResponse>> vapidKey() {
        return ResponseEntity.ok(ApiResponse.success(pushService.vapidKey()));
    }

    @PostMapping("/api/v1/me/push-subscriptions")
    @Operation(summary = "Turn on browser notifications", description = "Send the browser's PushSubscription.toJSON(). "
            + "Only known push services are accepted; at most 10 devices.")
    public ResponseEntity<ApiResponse<PushDtos.SubscriptionStatus>> subscribe(
            @Valid @RequestBody PushDtos.SubscribeRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success("Browser notifications on",
                pushService.subscribe(user.getUserId(), request, userAgent)));
    }

    @DeleteMapping("/api/v1/me/push-subscriptions")
    @Operation(summary = "Turn off browser notifications", description = "For the browser with this endpoint.")
    public ResponseEntity<ApiResponse<PushDtos.SubscriptionStatus>> unsubscribe(
            @Valid @RequestBody PushDtos.UnsubscribeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success("Browser notifications off",
                pushService.unsubscribe(user.getUserId(), request.endpoint())));
    }

    @GetMapping("/api/v1/me/push-preferences")
    @Operation(summary = "Which notifications are pushed", description = "Every type; all are pushed until muted.")
    public ResponseEntity<ApiResponse<List<PushDtos.Preference>>> preferences(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(pushService.preferences(user.getUserId())));
    }

    @PutMapping("/api/v1/me/push-preferences")
    @Operation(summary = "Change which notifications are pushed", description = "Only the listed types change.")
    public ResponseEntity<ApiResponse<List<PushDtos.Preference>>> updatePreferences(
            @Valid @RequestBody PushDtos.UpdatePreferences request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success("Push preferences saved",
                pushService.updatePreferences(user.getUserId(), request.preferences())));
    }
}
