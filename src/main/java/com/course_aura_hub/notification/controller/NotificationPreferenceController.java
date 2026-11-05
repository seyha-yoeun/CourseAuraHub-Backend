package com.course_aura_hub.notification.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.notification.email.EmailPreferenceDtos;
import com.course_aura_hub.notification.email.NotificationEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Notification emails", description = "Which notifications reach you by email, and one-click unsubscribe")
public class NotificationPreferenceController {

    private final NotificationEmailService emailService;

    @GetMapping("/api/v1/me/notification-preferences")
    @Operation(summary = "My email preferences", description = "Every notification type with my mode (instant, digest or off) and its default.")
    public ResponseEntity<ApiResponse<List<EmailPreferenceDtos.Preference>>> mine(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(emailService.findMine(user.getUserId())));
    }

    @PutMapping("/api/v1/me/notification-preferences")
    @Operation(summary = "Change my email preferences", description = "Only the listed types change.")
    public ResponseEntity<ApiResponse<List<EmailPreferenceDtos.Preference>>> update(
            @Valid @RequestBody EmailPreferenceDtos.UpdateRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success("Email preferences saved",
                emailService.update(user.getUserId(), request.preferences())));
    }

    @RequestMapping(value = "/api/v1/notifications/unsubscribe", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(
            summary = "Unsubscribe from notification emails",
            description = "Public, authorised by the signed token in the email link. scope is a notification type or all. "
                    + "POST supports mail clients' one-click unsubscribe."
    )
    public ResponseEntity<ApiResponse<Void>> unsubscribe(
            @RequestParam UUID user,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam String token
    ) {
        if (!emailService.unsubscribe(user, scope, token)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("This unsubscribe link is not valid"));
        }
        return ResponseEntity.ok(ApiResponse.success("You won't get these emails any more. Change this anytime in your email settings.", null));
    }
}
