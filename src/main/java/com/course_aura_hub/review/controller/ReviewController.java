package com.course_aura_hub.review.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.SecurityUtils;
import com.course_aura_hub.review.dto.ReviewPageResponse;
import com.course_aura_hub.review.dto.ReviewRequest;
import com.course_aura_hub.review.dto.ReviewResponse;
import com.course_aura_hub.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Course reviews and ratings")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/v1/courses/{slug}/reviews")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List course reviews", description = "Paged, newest first. Reviewer email is never exposed.")
    public ResponseEntity<ApiResponse<ReviewPageResponse>> findByCourse(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.findByCourse(slug, page, size, SecurityUtils.isAdmin(authentication))
        ));
    }

    @DeleteMapping("/api/v1/courses/{slug}/reviews/{reviewId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a review", description = "Admin-only moderation.")
    public ResponseEntity<ApiResponse<Void>> deleteAsAdmin(@PathVariable String slug, @PathVariable Integer reviewId) {
        log.info("Received admin delete review request courseSlug={} reviewId={}", slug, reviewId);
        reviewService.deleteAsAdmin(slug, reviewId);
        return ResponseEntity.ok(ApiResponse.success("Review deleted successfully", null));
    }

    @GetMapping("/api/v1/me/courses/{slug}/review")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my review", description = "Returns the caller's review of the course, or null data if none.")
    public ResponseEntity<ApiResponse<ReviewResponse>> findMine(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.findMine(authentication.getName(), slug).orElse(null)
        ));
    }

    @PutMapping("/api/v1/me/courses/{slug}/review")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create or update my review", description = "Requires enrollment. One review per learner per course.")
    public ResponseEntity<ApiResponse<ReviewResponse>> upsertMine(
            @PathVariable String slug,
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Review saved successfully",
                reviewService.upsertMine(authentication.getName(), slug, request)
        ));
    }

    @DeleteMapping("/api/v1/me/courses/{slug}/review")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete my review")
    public ResponseEntity<ApiResponse<Void>> deleteMine(@PathVariable String slug, Authentication authentication) {
        reviewService.deleteMine(authentication.getName(), slug);
        return ResponseEntity.ok(ApiResponse.success("Review deleted successfully", null));
    }
}
