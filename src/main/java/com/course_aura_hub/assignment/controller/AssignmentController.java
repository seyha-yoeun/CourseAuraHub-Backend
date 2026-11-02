package com.course_aura_hub.assignment.controller;

import com.course_aura_hub.assignment.dto.AssignmentGradeRequest;
import com.course_aura_hub.assignment.dto.AssignmentRequest;
import com.course_aura_hub.assignment.dto.AssignmentResponse;
import com.course_aura_hub.assignment.dto.AssignmentSubmitRequest;
import com.course_aura_hub.assignment.dto.SubmissionPageResponse;
import com.course_aura_hub.assignment.dto.SubmissionReviewResponse;
import com.course_aura_hub.assignment.service.AssignmentService;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Assignments", description = "Work learners submit for grading")
public class AssignmentController {

    private static final String STAFF = "hasAnyRole('ADMIN', 'INSTRUCTOR')";

    private final AssignmentService assignmentService;

    @GetMapping("/api/v1/me/lessons/{lessonId}/assignment")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "A lesson's assignment", description = "With my submission. Data is null when the lesson has none.")
    public ResponseEntity<ApiResponse<AssignmentResponse>> mine(
            @PathVariable Integer lessonId,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.findForLearner(Actor.of(user), lessonId)));
    }

    @PostMapping("/api/v1/me/lessons/{lessonId}/assignment/submission")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit work", description = "Replaces an earlier submission, until one has passed.")
    public ResponseEntity<ApiResponse<AssignmentResponse>> submit(
            @PathVariable Integer lessonId,
            @Valid @RequestBody AssignmentSubmitRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.submit(Actor.of(user), lessonId, request)));
    }

    @GetMapping("/api/v1/instructor/lessons/{lessonId}/assignment")
    @PreAuthorize(STAFF)
    @Operation(summary = "Get a lesson's assignment", description = "Admins and the course's instructor.")
    public ResponseEntity<ApiResponse<AssignmentResponse>> find(
            @PathVariable Integer lessonId,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.findForStaff(Actor.of(user), lessonId)));
    }

    @PutMapping("/api/v1/instructor/lessons/{lessonId}/assignment")
    @PreAuthorize(STAFF)
    @Operation(summary = "Create or update a lesson's assignment", description = "Admins and the course's instructor.")
    public ResponseEntity<ApiResponse<AssignmentResponse>> save(
            @PathVariable Integer lessonId,
            @Valid @RequestBody AssignmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.save(Actor.of(user), lessonId, request)));
    }

    @DeleteMapping("/api/v1/instructor/lessons/{lessonId}/assignment")
    @PreAuthorize(STAFF)
    @Operation(summary = "Remove a lesson's assignment", description = "Also deletes its submissions.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Integer lessonId,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        assignmentService.delete(Actor.of(user), lessonId);
        return ResponseEntity.ok(ApiResponse.success("Assignment removed", null));
    }

    @GetMapping("/api/v1/instructor/submissions")
    @PreAuthorize(STAFF)
    @Operation(summary = "Work to grade", description = "Every course for admins, their own for instructors. Oldest first.")
    public ResponseEntity<ApiResponse<SubmissionPageResponse>> ungraded(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.findUngraded(Actor.of(user), page, size)));
    }

    @PostMapping("/api/v1/instructor/submissions/{id}/grade")
    @PreAuthorize(STAFF)
    @Operation(summary = "Grade a submission", description = "A passing score completes the lesson.")
    public ResponseEntity<ApiResponse<SubmissionReviewResponse>> grade(
            @PathVariable Integer id,
            @Valid @RequestBody AssignmentGradeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.grade(Actor.of(user), id, request)));
    }
}
