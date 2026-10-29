package com.course_aura_hub.auth.controller;

import com.course_aura_hub.auth.dto.SessionResponse;
import com.course_aura_hub.auth.model.SessionRevocationReason;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.auth.service.RefreshSessionService;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "List and revoke the signed-in user's devices")
public class SessionController {

    private final RefreshSessionService refreshSessionService;

    @GetMapping
    @Operation(summary = "List my active sessions", description = "One entry per signed-in device; 'current' marks the caller.")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> list(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                refreshSessionService.listActiveSessions(principal.getUserId(), principal.getSessionId())));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Revoke a session", description = "Signs out that device immediately, including its access token.")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        refreshSessionService.revokeSession(principal.getUserId(), sessionId, SessionRevocationReason.REVOKED_BY_USER);
        return ResponseEntity.ok(ApiResponse.success("Session revoked", null));
    }
}
