package com.course_aura_hub.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Streaming details for one lesson, only for viewers who can watch it. */
public final class VideoDtos {

    private VideoDtos() {
    }

    /** @param fileRef the storage:// reference of an uploaded lesson-caption file */
    public record CaptionRequest(
            @NotBlank(message = "Upload a WebVTT file first") @Size(max = 500) String fileRef,
            @NotBlank(message = "Label is required") @Size(max = 50) String label
    ) {
    }

    /** @param url a short-lived signed link to the WebVTT file */
    public record Caption(String language, String label, String url) {
    }

    /**
     * @param status     processing, ready or failed; null when the lesson has no uploaded video
     * @param streamUrl  API path of the HLS playlist, once ready; play the original file until then
     */
    public record Media(Integer lessonId, String status, String streamUrl, Integer durationSeconds,
                        List<Caption> captions) {
    }

    public record Transcript(Integer lessonId, String language, String text) {
    }
}
