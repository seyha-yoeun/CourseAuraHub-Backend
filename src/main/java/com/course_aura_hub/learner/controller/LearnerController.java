package com.course_aura_hub.learner.controller;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.security.AuthCookieService;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.learner.dto.ChangePasswordRequest;
import com.course_aura_hub.learner.dto.LearnerProfileResponse;
import com.course_aura_hub.learner.dto.LearnerProfileUpdateRequest;
import com.course_aura_hub.learner.dto.MyCourseResponse;
import com.course_aura_hub.learner.dto.SelfEnrollRequest;
import com.course_aura_hub.learner.dto.WishlistItemResponse;
import com.course_aura_hub.learner.facade.LearnerFacade;
import com.course_aura_hub.learner.service.ProfileService;
import com.course_aura_hub.learner.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "My Learning", description = "Self-service endpoints for the authenticated learner")
public class LearnerController {

    private final LearnerFacade learnerFacade;
    private final ProfileService profileService;
    private final WishlistService wishlistService;
    private final AuthCookieService authCookieService;

    @GetMapping("/courses")
    @Operation(
            summary = "My courses",
            description = "Courses I'm enrolled in with progress, ordered by most recent activity."
    )
    public ResponseEntity<ApiResponse<List<MyCourseResponse>>> myCourses(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(learnerFacade.findMyCourses(authentication.getName())));
    }

    @PostMapping("/enrollments")
    @Operation(
            summary = "Enroll in a course",
            description = "Self-enrollment for free published courses. Paid courses are rejected until checkout exists."
    )
    public ResponseEntity<ApiResponse<MyCourseResponse>> enroll(
            @Valid @RequestBody SelfEnrollRequest request,
            Authentication authentication
    ) {
        log.info("Received self-enroll request courseSlug={}", request.courseSlug());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Enrolled successfully",
                        learnerFacade.enroll(authentication.getName(), request.courseSlug())
                ));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get my profile")
    public ResponseEntity<ApiResponse<LearnerProfileResponse>> getProfile(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(profileService.getProfile(authentication.getName())));
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update my profile", description = "Partial update. Null fields are unchanged.")
    public ResponseEntity<ApiResponse<LearnerProfileResponse>> updateProfile(
            @Valid @RequestBody LearnerProfileUpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Profile updated successfully",
                profileService.updateProfile(authentication.getName(), request)
        ));
    }

    @PutMapping("/password")
    @Operation(
            summary = "Change my password",
            description = "Local accounts only. Returns a fresh token pair in the body and as auth cookies, "
                    + "and revokes other sessions' refresh tokens."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = learnerFacade.changePassword(authentication.getName(), request);
        authCookieService.setAuthCookies(response, authResponse);
        // Token-forwarding clients (the Nuxt server proxy) read the new pair from the body, like login
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", authResponse));
    }

    @GetMapping("/wishlist")
    @Operation(summary = "My wishlist", description = "Saved courses, newest first.")
    public ResponseEntity<ApiResponse<List<WishlistItemResponse>>> wishlist(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(wishlistService.findMine(authentication.getName())));
    }

    @PutMapping("/wishlist/{slug}")
    @Operation(summary = "Add to wishlist", description = "Idempotent. Rejected for courses I'm already enrolled in.")
    public ResponseEntity<ApiResponse<WishlistItemResponse>> addToWishlist(
            @PathVariable String slug,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Course saved to wishlist",
                wishlistService.add(authentication.getName(), slug)
        ));
    }

    @DeleteMapping("/wishlist/{slug}")
    @Operation(summary = "Remove from wishlist", description = "Idempotent.")
    public ResponseEntity<ApiResponse<Void>> removeFromWishlist(@PathVariable String slug, Authentication authentication) {
        wishlistService.remove(authentication.getName(), slug);
        return ResponseEntity.ok(ApiResponse.success("Course removed from wishlist", null));
    }
}
