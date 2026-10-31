package com.course_aura_hub.quiz.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.quiz.dto.QuizAdminRequest;
import com.course_aura_hub.quiz.dto.QuizAdminResponse;
import com.course_aura_hub.quiz.dto.QuizResponse;
import com.course_aura_hub.quiz.dto.QuizResultResponse;
import com.course_aura_hub.quiz.dto.QuizSubmitRequest;
import com.course_aura_hub.quiz.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Quizzes", description = "Graded quizzes for quiz lessons")
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/api/v1/me/lessons/{lessonId}/quiz")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a quiz", description = "For enrolled learners. Never includes the correct answers.")
    public ResponseEntity<ApiResponse<QuizResponse>> find(@PathVariable Integer lessonId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(quizService.findForLearner(authentication.getName(), lessonId)));
    }

    @PostMapping("/api/v1/me/lessons/{lessonId}/quiz/attempts")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Submit a quiz",
            description = "Graded on the server. Retakes are unlimited. Correct answers are shown once the quiz is passed, "
                    + "and passing completes the lesson."
    )
    public ResponseEntity<ApiResponse<QuizResultResponse>> submit(
            @PathVariable Integer lessonId,
            @Valid @RequestBody QuizSubmitRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                quizService.submit(authentication.getName(), lessonId, request.answers())
        ));
    }

    @GetMapping("/api/v1/admin/lessons/{lessonId}/quiz")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a quiz with its answers", description = "Admin-only.")
    public ResponseEntity<ApiResponse<QuizAdminResponse>> findForAdmin(@PathVariable Integer lessonId) {
        return ResponseEntity.ok(ApiResponse.success(quizService.findForAdmin(lessonId)));
    }

    @PutMapping("/api/v1/admin/lessons/{lessonId}/quiz")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Save a quiz", description = "Admin-only. Replaces the questions; past attempts are kept.")
    public ResponseEntity<ApiResponse<QuizAdminResponse>> save(
            @PathVariable Integer lessonId,
            @Valid @RequestBody QuizAdminRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(quizService.save(lessonId, request)));
    }
}
