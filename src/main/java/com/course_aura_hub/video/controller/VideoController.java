package com.course_aura_hub.video.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.video.dto.VideoDtos;
import com.course_aura_hub.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Video", description = "HLS streams, captions and transcripts for video lessons")
public class VideoController {

    private static final MediaType HLS = MediaType.parseMediaType("application/vnd.apple.mpegurl");

    private final VideoService videoService;

    @GetMapping("/api/v1/me/lessons/{lessonId}/media")
    @Operation(summary = "A video lesson's stream and captions", description = "For viewers who can watch the lesson. "
            + "streamUrl appears once the HLS stream is ready; until then play the lesson's own file.")
    public ResponseEntity<ApiResponse<VideoDtos.Media>> media(@PathVariable Integer lessonId,
                                                              @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(videoService.media(Actor.of(user), lessonId)));
    }

    @GetMapping("/api/v1/me/lessons/{lessonId}/stream.m3u8")
    @Operation(summary = "HLS playlist", description = "Each segment is a short-lived signed link, so fetch the "
            + "playlist again after a few hours.")
    public ResponseEntity<String> playlist(@PathVariable Integer lessonId, @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok()
                .contentType(HLS)
                .cacheControl(CacheControl.noStore())
                .body(videoService.playlist(Actor.of(user), lessonId));
    }

    @GetMapping("/api/v1/me/lessons/{lessonId}/transcript")
    @Operation(summary = "Lesson transcript", description = "The text of the lesson's captions in lang (en by default).")
    public ResponseEntity<ApiResponse<VideoDtos.Transcript>> transcript(@PathVariable Integer lessonId,
                                                                        @RequestParam(defaultValue = "en") String lang,
                                                                        @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(videoService.transcript(Actor.of(user), lessonId, lang)));
    }

    @PutMapping("/api/v1/courses/{slug}/lessons/{lessonId}/captions/{lang}")
    @PreAuthorize("@courseAccess.canManageLesson(authentication, #lessonId)")
    @Operation(summary = "Add or replace captions", description = "Course managers. Upload a WebVTT file with purpose "
            + "lesson_caption first, then send its storage reference. One file per language.")
    public ResponseEntity<ApiResponse<VideoDtos.Caption>> saveCaption(@PathVariable String slug,
                                                                      @PathVariable Integer lessonId,
                                                                      @PathVariable String lang,
                                                                      @Valid @RequestBody VideoDtos.CaptionRequest request,
                                                                      @AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success("Captions saved",
                videoService.saveCaption(Actor.of(user), slug, lessonId, lang, request)));
    }

    @DeleteMapping("/api/v1/courses/{slug}/lessons/{lessonId}/captions/{lang}")
    @PreAuthorize("@courseAccess.canManageLesson(authentication, #lessonId)")
    @Operation(summary = "Remove captions", description = "Course managers.")
    public ResponseEntity<ApiResponse<Void>> deleteCaption(@PathVariable String slug, @PathVariable Integer lessonId,
                                                           @PathVariable String lang,
                                                           @AuthenticationPrincipal AuthUserPrincipal user) {
        videoService.deleteCaption(Actor.of(user), slug, lessonId, lang);
        return ResponseEntity.ok(ApiResponse.success("Captions removed", null));
    }
}
