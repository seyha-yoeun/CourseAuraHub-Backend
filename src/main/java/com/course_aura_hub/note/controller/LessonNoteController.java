package com.course_aura_hub.note.controller;

import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.note.dto.LessonNoteRequest;
import com.course_aura_hub.note.dto.LessonNoteResponse;
import com.course_aura_hub.note.service.LessonNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Lesson notes", description = "A learner's private notes and video bookmarks")
public class LessonNoteController {

    private final LessonNoteService noteService;

    @GetMapping("/api/v1/me/lessons/{lessonId}/notes")
    @Operation(summary = "My notes on a lesson", description = "Newest first.")
    public ResponseEntity<ApiResponse<List<LessonNoteResponse>>> mine(
            @PathVariable Integer lessonId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(noteService.findMine(authentication.getName(), lessonId)));
    }

    @PostMapping("/api/v1/me/lessons/{lessonId}/notes")
    @Operation(summary = "Add a note or bookmark", description = "Needs text, a video position, or both.")
    public ResponseEntity<ApiResponse<LessonNoteResponse>> create(
            @PathVariable Integer lessonId,
            @Valid @RequestBody LessonNoteRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(noteService.create(authentication.getName(), lessonId, request)));
    }

    @PutMapping("/api/v1/me/notes/{noteId}")
    @Operation(summary = "Update a note")
    public ResponseEntity<ApiResponse<LessonNoteResponse>> update(
            @PathVariable Integer noteId,
            @Valid @RequestBody LessonNoteRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(noteService.update(authentication.getName(), noteId, request)));
    }

    @DeleteMapping("/api/v1/me/notes/{noteId}")
    @Operation(summary = "Delete a note")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer noteId, Authentication authentication) {
        noteService.delete(authentication.getName(), noteId);
        return ResponseEntity.ok(ApiResponse.success("Note deleted", null));
    }
}
