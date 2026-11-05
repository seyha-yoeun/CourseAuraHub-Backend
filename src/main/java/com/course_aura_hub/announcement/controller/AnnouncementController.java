package com.course_aura_hub.announcement.controller;

import com.course_aura_hub.announcement.dto.AnnouncementPageResponse;
import com.course_aura_hub.announcement.dto.AnnouncementRequest;
import com.course_aura_hub.announcement.dto.AnnouncementResponse;
import com.course_aura_hub.announcement.service.AnnouncementService;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

@RestController
@RequiredArgsConstructor
@Tag(name = "Announcements", description = "Messages from course staff to enrolled learners")
public class AnnouncementController {

    private static final String STAFF = "hasAnyRole('ADMIN', 'INSTRUCTOR')";
    private static final String MANAGE = "/api/v1/instructor/courses/{slug}/announcements";

    private final AnnouncementService announcementService;

    @PostMapping(MANAGE)
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Send an announcement",
            description = "Published courses only, at most 5 a day. Every enrolled learner is notified, "
                    + "including on Telegram when connected."
    )
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(
            @PathVariable String slug,
            @Valid @RequestBody AnnouncementRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Announcement sent", announcementService.create(Actor.of(user), slug, request)));
    }

    @GetMapping(MANAGE)
    @PreAuthorize(STAFF)
    @Operation(summary = "A course's announcements", description = "Newest first.")
    public ResponseEntity<ApiResponse<AnnouncementPageResponse>> history(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                announcementService.findForManager(Actor.of(user), slug, page, size)));
    }

    @DeleteMapping(MANAGE + "/{id}")
    @PreAuthorize(STAFF)
    @Operation(summary = "Delete an announcement", description = "Notifications already sent are kept.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String slug,
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        announcementService.delete(Actor.of(user), slug, id);
        return ResponseEntity.ok(ApiResponse.success("Announcement deleted", null));
    }

    @GetMapping("/api/v1/me/courses/{slug}/announcements")
    @Operation(summary = "Announcements for a course I'm taking", description = "Newest first. Enrolled learners only.")
    public ResponseEntity<ApiResponse<AnnouncementPageResponse>> forLearner(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                announcementService.findForLearner(Actor.of(user), slug, page, size)));
    }
}
