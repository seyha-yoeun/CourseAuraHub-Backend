package com.course_aura_hub.assistant.controller;

import com.course_aura_hub.assistant.dto.AssistantDtos;
import com.course_aura_hub.assistant.service.AssistantService;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.quiz.dto.QuizAdminRequest;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Study assistant", description = "An AI assistant that answers from a lesson's study text")
public class AssistantController {

    private static final String MANAGER = "@courseAccess.canManageLesson(authentication, #lessonId)";

    private final AssistantService assistantService;

    @PutMapping("/api/v1/instructor/lessons/{lessonId}/assistant-source")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Set a lesson's study text", description = "Course managers. The text the assistant answers "
            + "from, such as the article or the video's transcript.")
    public ResponseEntity<ApiResponse<AssistantDtos.Source>> saveSource(@PathVariable Integer lessonId,
                                                                        @Valid @RequestBody AssistantDtos.SourceRequest request,
                                                                        @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Study text saved",
                assistantService.saveSource(Actor.of(user), lessonId, request.body())));
    }

    @PostMapping("/api/v1/instructor/lessons/{lessonId}/quiz/draft")
    @PreAuthorize(MANAGER)
    @Operation(summary = "Draft quiz questions", description = "Course managers. Questions drafted from the lesson's "
            + "study text, in the quiz editor's format. Nothing is saved or published.")
    public ResponseEntity<ApiResponse<List<QuizAdminRequest.Question>>> draftQuiz(
            @PathVariable Integer lessonId,
            @Valid @RequestBody(required = false) AssistantDtos.DraftRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user) {
        int count = request == null || request.count() == null ? 5 : request.count();
        return ResponseEntity.ok(ApiResponse.success(assistantService.draftQuiz(Actor.of(user), lessonId, count)));
    }

    // No produces: errors before the stream opens must still render as JSON; the emitter sets text/event-stream
    @PostMapping("/api/v1/me/lessons/{lessonId}/assistant/messages")
    @Operation(summary = "Ask the study assistant", description = "Enrolled learners the lesson is open to. Streams "
            + "server-sent events: conversation, delta (the answer in pieces), then done or error. Rate-limited, with "
            + "a daily allowance.")
    public SseEmitter ask(@PathVariable Integer lessonId, @Valid @RequestBody AssistantDtos.AskRequest request,
                          @AuthenticationPrincipal AuthUserPrincipal user) {
        return assistantService.ask(Actor.of(user), lessonId, request);
    }

    @GetMapping("/api/v1/me/lessons/{lessonId}/assistant/conversations")
    @Operation(summary = "My assistant conversations about a lesson", description = "The last five, newest first.")
    public ResponseEntity<ApiResponse<List<AssistantDtos.Conversation>>> conversations(
            @PathVariable Integer lessonId, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(assistantService.conversations(Actor.of(user), lessonId)));
    }

    @GetMapping("/api/v1/me/assistant/usage")
    @Operation(summary = "My assistant allowance today", description = "Resets at midnight UTC.")
    public ResponseEntity<ApiResponse<AssistantDtos.Usage>> usage(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(assistantService.usage(Actor.of(user))));
    }

    @GetMapping("/api/v1/me/lessons/{lessonId}/summary")
    @Operation(summary = "Lesson summary", description = "Made once per version of the lesson's study text and reused.")
    public ResponseEntity<ApiResponse<AssistantDtos.Summary>> summary(@PathVariable Integer lessonId,
                                                                      @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(assistantService.summary(Actor.of(user), lessonId)));
    }
}
