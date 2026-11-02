package com.course_aura_hub.reminder.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.reminder.dto.ReminderPreferenceRequest;
import com.course_aura_hub.reminder.dto.ReminderPreferenceResponse;
import com.course_aura_hub.reminder.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/reminders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Reminders", description = "Nudges to return to an unfinished course")
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping
    @Operation(summary = "Whether I get reminders")
    public ResponseEntity<ApiResponse<ReminderPreferenceResponse>> find(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                new ReminderPreferenceResponse(reminderService.isEnabled(authentication.getName()))));
    }

    @PutMapping
    @Operation(summary = "Turn reminders on or off")
    public ResponseEntity<ApiResponse<ReminderPreferenceResponse>> update(
            @Valid @RequestBody ReminderPreferenceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(new ReminderPreferenceResponse(
                reminderService.setEnabled(authentication.getName(), request.enabled()))));
    }
}
