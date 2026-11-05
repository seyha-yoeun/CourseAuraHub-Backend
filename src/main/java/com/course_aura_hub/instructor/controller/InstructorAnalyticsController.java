package com.course_aura_hub.instructor.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.instructor.dto.CourseAnalyticsResponse;
import com.course_aura_hub.instructor.service.InstructorAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Instructors", description = "An instructor's own courses, and the admin review that publishes them")
public class InstructorAnalyticsController {

    private final InstructorAnalyticsService analyticsService;

    @GetMapping("/api/v1/instructor/courses/{slug}/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @Operation(
            summary = "Course analytics",
            description = "Completion funnel by lesson with drop-off, video watch depth, quiz pass rates, "
                    + "assignment backlog, rating distribution and monthly enrollments. Aggregates only."
    )
    public ResponseEntity<ApiResponse<CourseAnalyticsResponse>> analytics(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.courseAnalytics(Actor.of(user), slug)));
    }
}
