package com.course_aura_hub.coding.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.coding.dto.CodingDtos;
import com.course_aura_hub.coding.service.CodingService;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Coding exercises", description = "CODE lessons graded in a sandbox")
public class CodingController {

    private static final String MANAGER = "@courseAccess.canManageLesson(authentication, #lessonId)";

    private final CodingService codingService;

    @GetMapping("/api/v1/instructor/lessons/{lessonId}/exercise")
    @PreAuthorize(MANAGER)
    @Operation(summary = "A lesson's exercise", description = "Course managers, with hidden tests. Null when none yet.")
    public ResponseEntity<ApiResponse<CodingDtos.Exercise>> forStaff(@PathVariable Integer lessonId,
                                                                    @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(codingService.findForStaff(Actor.of(user), lessonId)));
    }

    @PutMapping("/api/v1/instructor/lessons/{lessonId}/exercise")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Write a lesson's exercise", description = "Course managers. CODE lessons only. Examples are "
            + "shown to learners; 1 to 20 hidden tests decide whether the code passes.")
    public ResponseEntity<ApiResponse<CodingDtos.Exercise>> save(@PathVariable Integer lessonId,
                                                                @Valid @RequestBody CodingDtos.ExerciseRequest request,
                                                                @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Exercise saved", codingService.save(Actor.of(user), lessonId, request)));
    }

    @GetMapping("/api/v1/me/lessons/{lessonId}/exercise")
    @Operation(summary = "The exercise of a lesson I'm taking", description = "Instructions, starter code and "
            + "examples; hidden tests only as a count.")
    public ResponseEntity<ApiResponse<CodingDtos.LearnerExercise>> mine(@PathVariable Integer lessonId,
                                                                       @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(codingService.findForLearner(Actor.of(user), lessonId)));
    }

    @PostMapping("/api/v1/me/lessons/{lessonId}/exercise/runs")
    @Operation(summary = "Run my code", description = "Runs in a sandbox against the examples and hidden tests; "
            + "rate-limited. Passing every test completes the lesson.")
    public ResponseEntity<ApiResponse<CodingDtos.RunResult>> run(@PathVariable Integer lessonId,
                                                                @Valid @RequestBody CodingDtos.RunRequest request,
                                                                @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(codingService.run(Actor.of(user), lessonId, request.code())));
    }
}
