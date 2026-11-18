package com.course_aura_hub.live.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.live.dto.LiveDtos;
import com.course_aura_hub.live.service.LiveSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Live sessions", description = "Scheduled live classes, attendance and a calendar feed")
public class LiveSessionController {

    private static final String STAFF = "hasAnyRole('ADMIN', 'INSTRUCTOR')";
    private static final String MANAGE = "/api/v1/instructor/courses/{slug}/live-sessions";

    private final LiveSessionService liveSessionService;

    @GetMapping(MANAGE)
    @PreAuthorize(STAFF)
    @Operation(summary = "A course's live sessions", description = "Course managers. With meeting links and attendance counts.")
    public ResponseEntity<ApiResponse<List<LiveDtos.ManagedSession>>> forCourse(
            @PathVariable String slug, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.findForCourse(Actor.of(user), slug)));
    }

    @PostMapping(MANAGE)
    @PreAuthorize(STAFF)
    @Operation(summary = "Schedule a live session", description = "Course managers. https meeting links only; "
            + "enrolled learners are notified and reminded an hour and ten minutes before.")
    public ResponseEntity<ApiResponse<LiveDtos.ManagedSession>> create(
            @PathVariable String slug, @Valid @RequestBody LiveDtos.SessionRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Live session scheduled",
                liveSessionService.create(Actor.of(user), slug, request)));
    }

    @PutMapping(MANAGE + "/{id}")
    @PreAuthorize(STAFF)
    @Operation(summary = "Change a live session", description = "Course managers. Moving it tells learners and resets reminders.")
    public ResponseEntity<ApiResponse<LiveDtos.ManagedSession>> update(
            @PathVariable String slug, @PathVariable Integer id, @Valid @RequestBody LiveDtos.SessionRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Live session updated",
                liveSessionService.update(Actor.of(user), slug, id, request)));
    }

    @DeleteMapping(MANAGE + "/{id}")
    @PreAuthorize(STAFF)
    @Operation(summary = "Cancel a live session", description = "Course managers. Learners are told; attendance is kept.")
    public ResponseEntity<ApiResponse<LiveDtos.ManagedSession>> cancel(
            @PathVariable String slug, @PathVariable Integer id, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Live session cancelled",
                liveSessionService.cancel(Actor.of(user), slug, id)));
    }

    @GetMapping("/api/v1/me/live-sessions")
    @Operation(summary = "My live sessions", description = "Sessions in my courses that haven't ended, and the last "
            + "week's. Meeting links come only from joining.")
    public ResponseEntity<ApiResponse<List<LiveDtos.LearnerSession>>> mine(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.findMine(authentication.getName())));
    }

    @PostMapping("/api/v1/me/live-sessions/{id}/join")
    @Operation(summary = "Join a live session", description = "Enrolled learners, from shortly before the start until "
            + "the end. Records attendance and returns the meeting link.")
    public ResponseEntity<ApiResponse<LiveDtos.JoinResponse>> join(@PathVariable Integer id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.join(authentication.getName(), id)));
    }

    @PostMapping("/api/v1/me/calendar-token")
    @Operation(summary = "Get a calendar feed link", description = "A private ICS link for calendar apps. Calling it "
            + "again replaces the link, so an old one stops working.")
    public ResponseEntity<ApiResponse<LiveDtos.CalendarFeed>> calendarToken(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.rotateCalendarToken(user.getUserId())));
    }

    @GetMapping(value = "/api/v1/calendar/{token}.ics", produces = "text/calendar;charset=UTF-8")
    @Operation(summary = "Calendar feed", description = "Public, authorised by the secret token in the link.")
    public ResponseEntity<String> calendar(@PathVariable String token) {
        String body = liveSessionService.calendar(token);
        if (body == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
