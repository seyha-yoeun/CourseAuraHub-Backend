package com.course_aura_hub.discussion.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.discussion.dto.DiscussionPostRequest;
import com.course_aura_hub.discussion.dto.LessonAnswerResponse;
import com.course_aura_hub.discussion.dto.LessonQuestionResponse;
import com.course_aura_hub.discussion.dto.OpenQuestionPageResponse;
import com.course_aura_hub.discussion.dto.QuestionResolvedRequest;
import com.course_aura_hub.discussion.service.DiscussionService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Lesson Q&A", description = "Questions and answers on a lesson")
public class DiscussionController {

    private final DiscussionService discussionService;

    @GetMapping("/api/v1/me/lessons/{lessonId}/questions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "A lesson's questions", description = "For enrolled learners and the course's staff. Newest first.")
    public ResponseEntity<ApiResponse<List<LessonQuestionResponse>>> questions(
            @PathVariable Integer lessonId,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(discussionService.findQuestions(Actor.of(user), lessonId)));
    }

    @PostMapping("/api/v1/me/lessons/{lessonId}/questions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ask a question")
    public ResponseEntity<ApiResponse<LessonQuestionResponse>> ask(
            @PathVariable Integer lessonId,
            @Valid @RequestBody DiscussionPostRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(discussionService.ask(Actor.of(user), lessonId, request.body())));
    }

    @PostMapping("/api/v1/me/questions/{questionId}/answers")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Answer a question")
    public ResponseEntity<ApiResponse<LessonAnswerResponse>> answer(
            @PathVariable Integer questionId,
            @Valid @RequestBody DiscussionPostRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(discussionService.answer(Actor.of(user), questionId, request.body())));
    }

    @PatchMapping("/api/v1/me/questions/{questionId}/resolved")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark a question resolved", description = "For its author and the course's staff.")
    public ResponseEntity<ApiResponse<LessonQuestionResponse>> setResolved(
            @PathVariable Integer questionId,
            @Valid @RequestBody QuestionResolvedRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                discussionService.setResolved(Actor.of(user), questionId, request.resolved())));
    }

    @DeleteMapping("/api/v1/me/questions/{questionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a question", description = "For its author and the course's staff.")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable Integer questionId,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        discussionService.deleteQuestion(Actor.of(user), questionId);
        return ResponseEntity.ok(ApiResponse.success("Question deleted", null));
    }

    @DeleteMapping("/api/v1/me/answers/{answerId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete an answer", description = "For its author and the course's staff.")
    public ResponseEntity<ApiResponse<Void>> deleteAnswer(
            @PathVariable Integer answerId,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        discussionService.deleteAnswer(Actor.of(user), answerId);
        return ResponseEntity.ok(ApiResponse.success("Answer deleted", null));
    }

    @GetMapping("/api/v1/instructor/questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @Operation(summary = "Open questions", description = "Every course for admins, their own for instructors. Newest first.")
    public ResponseEntity<ApiResponse<OpenQuestionPageResponse>> open(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.ok(ApiResponse.success(discussionService.findOpen(Actor.of(user), page, size)));
    }
}
