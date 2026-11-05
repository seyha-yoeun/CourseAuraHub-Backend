package com.course_aura_hub.instructor.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.instructor.dto.InstructorCardResponse;
import com.course_aura_hub.instructor.dto.InstructorProfileRequest;
import com.course_aura_hub.instructor.dto.InstructorProfileResponse;
import com.course_aura_hub.instructor.dto.PublicInstructorResponse;
import com.course_aura_hub.instructor.service.InstructorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Instructor profiles", description = "Public instructor pages and the instructor's own profile settings")
public class InstructorProfileController {

    private static final String STAFF = "hasAnyRole('ADMIN', 'INSTRUCTOR')";

    private final InstructorProfileService profileService;

    @GetMapping("/api/v1/instructor/profile")
    @PreAuthorize(STAFF)
    @Operation(summary = "My instructor profile", description = "Created with a handle from my name the first time.")
    public ResponseEntity<ApiResponse<InstructorProfileResponse>> mine(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(profileService.findMine(Actor.of(user))));
    }

    @PutMapping("/api/v1/instructor/profile")
    @PreAuthorize(STAFF)
    @Operation(
            summary = "Update my instructor profile",
            description = "Handle, headline and social links. Name, bio, avatar, website and Twitter come from /me/profile."
    )
    public ResponseEntity<ApiResponse<InstructorProfileResponse>> update(
            @Valid @RequestBody InstructorProfileRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated", profileService.updateMine(Actor.of(user), request)));
    }

    @GetMapping("/api/v1/instructors")
    @Operation(summary = "Instructors", description = "Public. Instructors with a published course, most learners first.")
    public ResponseEntity<ApiResponse<List<InstructorCardResponse>>> list(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(profileService.findListed(limit)));
    }

    @GetMapping("/api/v1/instructors/{handle}")
    @Operation(summary = "Instructor page", description = "Public. Bio, links, totals and published courses.")
    public ResponseEntity<ApiResponse<PublicInstructorResponse>> byHandle(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.success(profileService.findByHandle(handle)));
    }

    @GetMapping("/api/v1/courses/{slug}/instructor")
    @Operation(summary = "A course's instructor", description = "Public. 404 when the course is admin-managed or its owner has no profile.")
    public ResponseEntity<ApiResponse<InstructorCardResponse>> forCourse(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(profileService.findForCourse(slug)));
    }
}
