package com.course_aura_hub.prerequisite.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.common.security.SecurityUtils;
import com.course_aura_hub.prerequisite.dto.PrerequisiteDtos;
import com.course_aura_hub.prerequisite.service.PrerequisiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Prerequisites", description = "Courses to finish before another")
public class PrerequisiteController {

    private final PrerequisiteService prerequisiteService;

    @GetMapping("/api/v1/courses/{slug}/prerequisites")
    @Operation(summary = "Course prerequisites", description = "Public. Signed-in learners also see which they completed and whether they can enroll.")
    public ResponseEntity<ApiResponse<PrerequisiteDtos.Requirements>> find(@PathVariable String slug, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(prerequisiteService.find(slug, SecurityUtils.emailOf(authentication))));
    }

    @PutMapping("/api/v1/instructor/courses/{slug}/prerequisites")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @Operation(summary = "Set prerequisites", description = "Admins and the course's instructor. Replaces the list; hard defaults to true; cycles are rejected.")
    public ResponseEntity<ApiResponse<List<PrerequisiteDtos.Prerequisite>>> replace(
            @PathVariable String slug, @Valid @RequestBody PrerequisiteDtos.SetRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Prerequisites saved",
                prerequisiteService.replace(Actor.of(user), slug, request.prerequisites())));
    }
}
