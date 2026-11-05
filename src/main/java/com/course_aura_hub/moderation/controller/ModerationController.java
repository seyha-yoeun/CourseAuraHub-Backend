package com.course_aura_hub.moderation.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.moderation.dto.ModerationDtos;
import com.course_aura_hub.moderation.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Moderation", description = "Reports on reviews, questions and answers")
public class ModerationController {

    private final ModerationService moderationService;

    @PostMapping("/api/v1/me/reports")
    @Operation(summary = "Report content", description = "A review, question or answer that breaks the rules. Reporting the same item twice does nothing.")
    public ResponseEntity<ApiResponse<Void>> report(@Valid @RequestBody ModerationDtos.ReportRequest request,
                                                    @AuthenticationPrincipal AuthUserPrincipal user) {
        moderationService.report(Actor.of(user), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("Thanks, an admin will take a look", null));
    }

    @GetMapping("/api/v1/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Open reports", description = "Admin-only. Oldest first, with the reported text.")
    public ResponseEntity<ApiResponse<ModerationDtos.Page>> open(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(moderationService.findOpen(page, size)));
    }

    @PostMapping("/api/v1/admin/reports/{id}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Hide the reported content", description = "Admin-only. Closes every open report on it; a hidden review leaves the rating.")
    public ResponseEntity<ApiResponse<ModerationDtos.Report>> hide(@PathVariable Integer id, @AuthenticationPrincipal AuthUserPrincipal admin) {
        return ResponseEntity.ok(ApiResponse.success("Content hidden", moderationService.hide(id, admin.getUserId())));
    }

    @PostMapping("/api/v1/admin/reports/{id}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dismiss a report", description = "Admin-only. The content stays visible.")
    public ResponseEntity<ApiResponse<ModerationDtos.Report>> dismiss(@PathVariable Integer id, @AuthenticationPrincipal AuthUserPrincipal admin) {
        return ResponseEntity.ok(ApiResponse.success("Report dismissed", moderationService.dismiss(id, admin.getUserId())));
    }
}
