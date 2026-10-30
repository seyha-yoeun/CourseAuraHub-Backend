package com.course_aura_hub.curriculum.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.curriculum.dto.LessonProgressResponse;
import com.course_aura_hub.curriculum.service.LessonProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/lessons")
@RequiredArgsConstructor
@Tag(name = "Learning Progress", description = "Lesson completion tracking for the current learner")
public class LessonProgressController {

    private final LessonProgressService lessonProgressService;

    @PutMapping("/{lessonId}/completion")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Mark lesson complete",
            description = "Idempotent. Requires enrollment. Marks the course completed when every lesson is done."
    )
    public ResponseEntity<ApiResponse<LessonProgressResponse>> markComplete(
            @PathVariable Integer lessonId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lesson marked complete",
                lessonProgressService.markComplete(authentication.getName(), lessonId)
        ));
    }

    @DeleteMapping("/{lessonId}/completion")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark lesson incomplete", description = "Idempotent. Requires enrollment.")
    public ResponseEntity<ApiResponse<LessonProgressResponse>> markIncomplete(
            @PathVariable Integer lessonId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lesson marked incomplete",
                lessonProgressService.markIncomplete(authentication.getName(), lessonId)
        ));
    }
}
