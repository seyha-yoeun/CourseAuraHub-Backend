package com.course_aura_hub.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** AI assistant shapes. */
public final class AssistantDtos {

    private AssistantDtos() {
    }

    /** @param conversationId continue this conversation; null starts a new one */
    public record AskRequest(
            Long conversationId,
            @NotBlank(message = "Ask a question") @Size(max = 4000, message = "Questions must not exceed 4000 characters")
            String message
    ) {
    }

    public record SourceRequest(
            @NotBlank(message = "Paste the lesson's text") @Size(max = 150_000, message = "The text is too long")
            String body
    ) {
    }

    public record Source(Integer lessonId, int characters, Instant updatedAt) {
    }

    public record Message(Long id, String role, String content, Instant createdAt) {
    }

    public record Conversation(Long id, Integer lessonId, Instant createdAt, List<Message> messages) {
    }

    public record Summary(Integer lessonId, String summary) {
    }

    public record DraftRequest(
            @Min(value = 1, message = "Draft at least 1 question") @Max(value = 10, message = "Draft at most 10 questions")
            Integer count
    ) {
    }

    /** @param tokensLeft today's remaining allowance */
    public record Usage(long tokensUsed, long tokensLeft) {
    }
}
