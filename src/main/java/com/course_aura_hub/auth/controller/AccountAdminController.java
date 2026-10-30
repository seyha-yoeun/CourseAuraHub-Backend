package com.course_aura_hub.auth.controller;

import com.course_aura_hub.auth.dto.AccountStatusUpdateRequest;
import com.course_aura_hub.auth.dto.AdminUserStatusResponse;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.auth.service.AccountAdminService;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
@Tag(name = "Account administration", description = "Admin-only account status controls")
public class AccountAdminController {

    private final AccountAdminService accountAdminService;

    @GetMapping("/security")
    @Operation(summary = "Get account security status")
    public ResponseEntity<ApiResponse<AdminUserStatusResponse>> status(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(accountAdminService.getStatus(userId)));
    }

    @PatchMapping("/status")
    @Operation(summary = "Enable or disable an account", description = "Disabling revokes every session and access token of the account.")
    public ResponseEntity<ApiResponse<AdminUserStatusResponse>> setStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody AccountStatusUpdateRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success("Account status updated",
                accountAdminService.setEnabled(admin.getUserId(), userId, request.enabled())));
    }

    @PostMapping("/unlock")
    @Operation(summary = "Clear a temporary lockout")
    public ResponseEntity<ApiResponse<AdminUserStatusResponse>> unlock(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success("Account unlocked", accountAdminService.unlock(admin.getUserId(), userId)));
    }

    @DeleteMapping("/sessions")
    @Operation(summary = "Sign the account out everywhere")
    public ResponseEntity<ApiResponse<Void>> revokeSessions(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        accountAdminService.revokeAllSessions(admin.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success("All sessions revoked", null));
    }
}
