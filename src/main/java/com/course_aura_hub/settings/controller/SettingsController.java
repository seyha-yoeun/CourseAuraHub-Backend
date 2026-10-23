package com.course_aura_hub.settings.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.settings.dto.PlatformSettingsResponse;
import com.course_aura_hub.settings.dto.PublicSettingsResponse;
import com.course_aura_hub.settings.dto.SettingsUpdateRequest;
import com.course_aura_hub.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Platform and user profile settings endpoints")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get platform settings", description = "Admin-only platform settings and the authenticated admin profile.")
    public ResponseEntity<ApiResponse<PlatformSettingsResponse>> getSettings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getSettings(authentication.getName())));
    }

    @GetMapping("/public")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get public platform settings", description = "Client-safe branding settings for authenticated users.")
    public ResponseEntity<ApiResponse<PublicSettingsResponse>> getPublicSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getPublicSettings()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update platform settings", description = "Admin-only update of platform branding, notification preferences, and current user profile.")
    public ResponseEntity<ApiResponse<PlatformSettingsResponse>> updateSettings(
            Authentication authentication,
            @Valid @RequestBody SettingsUpdateRequest request
    ) {
        log.info("Received update platform settings request");
        return ResponseEntity.ok(ApiResponse.success(
                "Settings updated successfully",
                settingsService.updateSettings(authentication.getName(), request)
        ));
    }
}
