package com.course_aura_hub.recommendation.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.course.dto.CourseResponse;
import com.course_aura_hub.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "Related courses and personal course suggestions")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/api/v1/courses/{slug}/related")
    @Operation(
            summary = "Related courses",
            description = "Public. Published courses that share tags or the category with this course, "
                    + "or that its learners also took. Falls back to the most popular courses."
    )
    public ResponseEntity<ApiResponse<List<CourseResponse>>> related(
            @PathVariable String slug,
            @Parameter(description = "Number of courses, 1 to 20; defaults to 6")
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(recommendationService.related(slug, limit)));
    }

    @GetMapping("/api/v1/me/recommendations")
    @Operation(
            summary = "Recommended for me",
            description = "Published courses based on my enrolled and wishlisted courses, which are left out. "
                    + "Learners with no history get the most popular courses."
    )
    public ResponseEntity<ApiResponse<List<CourseResponse>>> mine(
            @Parameter(description = "Number of courses, 1 to 20; defaults to 6")
            @RequestParam(required = false) Integer limit,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                recommendationService.forLearner(authentication.getName(), limit)));
    }
}
