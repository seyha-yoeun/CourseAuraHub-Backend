package com.course_aura_hub.instructor.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.course.dto.CourseCreateRequest;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.course.dto.CourseUpdateRequest;
import com.course_aura_hub.instructor.dto.CourseReviewRejectRequest;
import com.course_aura_hub.instructor.dto.InstructorCourseResponse;
import com.course_aura_hub.instructor.dto.InstructorStudentResponse;
import com.course_aura_hub.instructor.dto.InstructorSummaryResponse;
import com.course_aura_hub.instructor.service.InstructorService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Instructors", description = "An instructor's own courses, and the admin review that publishes them")
public class InstructorController {

    private static final String STAFF = "hasAnyRole('ADMIN', 'INSTRUCTOR')";
    private static final String BASE = "/api/v1/instructor";

    private final InstructorService instructorService;

    @GetMapping(BASE + "/summary")
    @PreAuthorize(STAFF)
    @Operation(summary = "My teaching totals")
    public ResponseEntity<ApiResponse<InstructorSummaryResponse>> summary(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(instructorService.summary(Actor.of(user))));
    }

    @GetMapping(BASE + "/courses")
    @PreAuthorize(STAFF)
    @Operation(summary = "My courses", description = "Every status, with sales figures.")
    public ResponseEntity<ApiResponse<List<InstructorCourseResponse>>> mine(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(instructorService.findMine(Actor.of(user))));
    }

    @PostMapping(BASE + "/courses")
    @PreAuthorize(STAFF)
    @Operation(summary = "Create a course", description = "Always a draft, under the caller's name.")
    public ResponseEntity<ApiResponse<InstructorCourseResponse>> create(
            @Valid @RequestBody CourseCreateRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(instructorService.create(Actor.of(user), request)));
    }

    @GetMapping(BASE + "/courses/{slug}")
    @PreAuthorize(STAFF)
    @Operation(summary = "One of my courses")
    public ResponseEntity<ApiResponse<InstructorCourseResponse>> find(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(instructorService.find(Actor.of(user), slug)));
    }

    @PutMapping(BASE + "/courses/{slug}")
    @PreAuthorize(STAFF)
    @Operation(summary = "Update my course", description = "The status can't be set here; only a review publishes a course.")
    public ResponseEntity<ApiResponse<InstructorCourseResponse>> update(
            @PathVariable String slug,
            @Valid @RequestBody CourseUpdateRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(instructorService.update(Actor.of(user), slug, request)));
    }

    @DeleteMapping(BASE + "/courses/{slug}")
    @PreAuthorize(STAFF)
    @Operation(summary = "Delete my draft", description = "Only a draft with no students.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        instructorService.delete(Actor.of(user), slug);
        return ResponseEntity.ok(ApiResponse.success("Course deleted", null));
    }

    @PostMapping(BASE + "/courses/{slug}/submit")
    @PreAuthorize(STAFF)
    @Operation(summary = "Send for review", description = "Needs at least one lesson. The course can't be edited during review.")
    public ResponseEntity<ApiResponse<InstructorCourseResponse>> submit(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(instructorService.submitForReview(Actor.of(user), slug)));
    }

    @GetMapping(BASE + "/courses/{slug}/students")
    @PreAuthorize(STAFF)
    @Operation(summary = "A course's learners", description = "Names and completion only, never contact details.")
    public ResponseEntity<ApiResponse<List<InstructorStudentResponse>>> students(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(instructorService.findStudents(Actor.of(user), slug)));
    }

    @GetMapping("/api/v1/admin/course-reviews")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Courses waiting for review", description = "Admin-only. Oldest first.")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> awaitingReview() {
        return ResponseEntity.ok(ApiResponse.success(instructorService.findAwaitingReview()));
    }

    @PostMapping("/api/v1/admin/course-reviews/{slug}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a course", description = "Admin-only. Publishes it.")
    public ResponseEntity<ApiResponse<CourseResponse>> approve(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(instructorService.approve(slug)));
    }

    @PostMapping("/api/v1/admin/course-reviews/{slug}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send a course back", description = "Admin-only. It returns to draft with the reason.")
    public ResponseEntity<ApiResponse<CourseResponse>> reject(
            @PathVariable String slug,
            @Valid @RequestBody CourseReviewRejectRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(instructorService.reject(slug, request.note())));
    }
}
