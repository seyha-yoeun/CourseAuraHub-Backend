package com.course_aura_hub.revision.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.revision.dto.RevisionDtos;
import com.course_aura_hub.revision.service.CourseRevisionService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Course revisions", description = "Changes to live instructor courses, reviewed by an admin before they go live")
public class CourseRevisionController {

    private static final String STAFF = "hasAnyRole('ADMIN', 'INSTRUCTOR')";
    private static final String MINE = "/api/v1/instructor/courses/{slug}/revisions";
    private static final String ADMIN = "/api/v1/admin/course-revisions";

    private final CourseRevisionService revisionService;

    @GetMapping(MINE)
    @PreAuthorize(STAFF)
    @Operation(summary = "A course's revisions", description = "Every proposed change to the live course, newest first.")
    public ResponseEntity<ApiResponse<List<RevisionDtos.RevisionResponse>>> history(
            @PathVariable String slug, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(revisionService.history(Actor.of(user), slug)));
    }

    @PatchMapping(MINE + "/pending")
    @PreAuthorize(STAFF)
    @Operation(summary = "Describe the pending changes", description = "A note for the admin, shown in the public changelog once approved.")
    public ResponseEntity<ApiResponse<RevisionDtos.RevisionResponse>> summary(
            @PathVariable String slug, @RequestBody Map<String, String> body, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(revisionService.updateSummary(Actor.of(user), slug, body.get("summary"))));
    }

    @DeleteMapping(MINE + "/pending")
    @PreAuthorize(STAFF)
    @Operation(summary = "Withdraw the pending changes", description = "The live course stays as it is.")
    public ResponseEntity<ApiResponse<Void>> withdraw(@PathVariable String slug, @AuthenticationPrincipal AuthUserPrincipal user) {
        revisionService.withdraw(Actor.of(user), slug);
        return ResponseEntity.ok(ApiResponse.success("Changes withdrawn", null));
    }

    @GetMapping(ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Changes waiting for review", description = "Admin-only. Oldest first, with each changed field's before and after.")
    public ResponseEntity<ApiResponse<List<RevisionDtos.RevisionResponse>>> awaiting() {
        return ResponseEntity.ok(ApiResponse.success(revisionService.findAwaitingReview()));
    }

    @GetMapping(ADMIN + "/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "A revision", description = "Admin-only.")
    public ResponseEntity<ApiResponse<RevisionDtos.RevisionResponse>> find(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(revisionService.find(id)));
    }

    @PostMapping(ADMIN + "/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve changes", description = "Admin-only. Applies them to the live course and tells enrolled learners about visible changes.")
    public ResponseEntity<ApiResponse<RevisionDtos.RevisionResponse>> approve(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) RevisionDtos.DecisionRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin) {
        return ResponseEntity.ok(ApiResponse.success("Changes are live",
                revisionService.approve(id, admin.getUserId(), request == null ? null : request.note())));
    }

    @PostMapping(ADMIN + "/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send changes back", description = "Admin-only. The note goes to the instructor; the live course is unchanged.")
    public ResponseEntity<ApiResponse<RevisionDtos.RevisionResponse>> reject(
            @PathVariable Integer id,
            @Valid @RequestBody RevisionDtos.DecisionRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin) {
        return ResponseEntity.ok(ApiResponse.success("Changes sent back", revisionService.reject(id, admin.getUserId(), request.note())));
    }

    @GetMapping("/api/v1/courses/{slug}/changelog")
    @Operation(summary = "Course changelog", description = "Public. Approved changes to a published course: fields changed and the instructor's note.")
    public ResponseEntity<ApiResponse<List<RevisionDtos.ChangelogEntry>>> changelog(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(revisionService.changelog(slug)));
    }
}
