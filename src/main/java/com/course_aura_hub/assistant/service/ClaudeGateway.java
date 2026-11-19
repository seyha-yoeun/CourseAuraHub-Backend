package com.course_aura_hub.assistant.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.BetaMessageAccumulator;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaRawMessageStreamEvent;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.course_aura_hub.assistant.config.AssistantProperties;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The only class that talks to the Anthropic API. Requests go to the beta endpoint for server-side refusal
 * fallbacks: if the model declines, the API retries on another model by refusal category. Called with no
 * transaction open.
 */
@Component
public class ClaudeGateway {

    private final ObjectProvider<AnthropicClient> client;
    private final AssistantProperties properties;

    public ClaudeGateway(ObjectProvider<AnthropicClient> client, AssistantProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** One earlier turn of the conversation. */
    public record Turn(boolean fromUser, String text) {
    }

    /** @param refused the model declined even after the fallback; text is then empty */
    public record Answer(String text, long inputTokens, long outputTokens, boolean refused) {
    }

    public boolean available() {
        return properties.isConfigured() && client.getIfAvailable() != null;
    }

    /** Streams an answer, handing each piece of text to {@code onText} as it arrives. */
    public Answer streamAnswer(String system, List<Turn> turns, Consumer<String> onText) {
        MessageCreateParams.Builder params = base(system, properties.maxAnswerTokens())
                .outputConfig(BetaOutputConfig.builder()
                        .effort(BetaOutputConfig.Effort.of(properties.chatEffort().toLowerCase(Locale.ROOT)))
                        .build());
        for (Turn turn : turns) {
            if (turn.fromUser()) {
                params.addUserMessage(turn.text());
            } else {
                params.addAssistantMessage(turn.text());
            }
        }

        BetaMessageAccumulator accumulator = BetaMessageAccumulator.create();
        try (StreamResponse<BetaRawMessageStreamEvent> stream = client().beta().messages().createStreaming(params.build())) {
            stream.stream().peek(accumulator::accumulate).forEach(event -> event.contentBlockDelta()
                    .flatMap(delta -> delta.delta().text())
                    .ifPresent(text -> onText.accept(text.text())));
        }
        return toAnswer(accumulator.message());
    }

    /** A complete answer at once, for short jobs such as a summary. */
    public Answer complete(String system, String prompt, long maxTokens) {
        BetaMessage message = client().beta().messages().create(base(system, maxTokens).addUserMessage(prompt).build());
        return toAnswer(message);
    }

    /** Quiz questions as typed JSON, through structured outputs. Null when the model declined. */
    public QuizDraftOutput draftQuiz(String system, String prompt) {
        StructuredMessageCreateParams<QuizDraftOutput> params = base(system, 16_000L)
                .outputConfig(QuizDraftOutput.class)
                .addUserMessage(prompt)
                .build();
        return client().beta().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElse(null);
    }

    private MessageCreateParams.Builder base(String system, long maxTokens) {
        return MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(maxTokens)
                .system(system)
                // The system prompt holds the whole lesson text, so repeat questions read it from the cache
                .cacheControl(BetaCacheControlEphemeral.builder().build())
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .fallbacksDefault();
    }

    private static Answer toAnswer(BetaMessage message) {
        boolean refused = message.stopReason().map(BetaStopReason.REFUSAL::equals).orElse(false);
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .collect(Collectors.joining());
        return new Answer(refused ? "" : text, message.usage().inputTokens(), message.usage().outputTokens(), refused);
    }

    private AnthropicClient client() {
        AnthropicClient anthropic = client.getIfAvailable();
        if (anthropic == null) {
            throw new ServiceUnavailableException("The study assistant is not available right now");
        }
        return anthropic;
    }
}
