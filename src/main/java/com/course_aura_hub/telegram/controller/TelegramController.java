package com.course_aura_hub.telegram.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.telegram.dto.TelegramConnectLinkResponse;
import com.course_aura_hub.telegram.dto.TelegramPreferenceRequest;
import com.course_aura_hub.telegram.dto.TelegramStatusResponse;
import com.course_aura_hub.telegram.service.TelegramLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/telegram")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Telegram", description = "The caller's Telegram connection")
public class TelegramController {

    private final TelegramLinkService linkService;

    @GetMapping
    @Operation(summary = "My Telegram connection")
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> status(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(linkService.status(user.getUserId())));
    }

    @PostMapping("/link")
    @Operation(summary = "Create a connect link", description = "One-time and short-lived. Replaces any earlier link.")
    public ResponseEntity<ApiResponse<TelegramConnectLinkResponse>> createLink(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(linkService.createConnectLink(user.getUserId())));
    }

    @PatchMapping
    @Operation(summary = "Pause or resume Telegram notifications")
    public ResponseEntity<ApiResponse<TelegramStatusResponse>> update(
            @Valid @RequestBody TelegramPreferenceRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(linkService.setEnabled(user.getUserId(), request.enabled())));
    }

    @DeleteMapping
    @Operation(summary = "Disconnect Telegram")
    public ResponseEntity<ApiResponse<Void>> disconnect(@AuthenticationPrincipal AuthUserPrincipal user) {
        linkService.disconnect(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Telegram disconnected", null));
    }
}
