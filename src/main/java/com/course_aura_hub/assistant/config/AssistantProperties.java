package com.course_aura_hub.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * The AI study assistant, through the Anthropic API. Off until an API key is set.
 *
 * @param apiKey             Anthropic API key; secret, from ANTHROPIC_API_KEY
 * @param model              Claude model id
 * @param chatEffort         effort for answers: low, medium, high, xhigh or max; chat does well at low
 * @param maxAnswerTokens    longest answer
 * @param dailyTokenLimit    tokens one learner may use per day, input and output together
 * @param requestsPerMinute  questions one learner may ask per minute
 * @param historyMessages    earlier messages of the conversation sent with each question
 * @param maxSourceChars     longest lesson study text, which goes to the model whole
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("claude-opus-5-5") String model,
        @DefaultValue("low") String chatEffort,
        @DefaultValue("4000") long maxAnswerTokens,
        @DefaultValue("100000") long dailyTokenLimit,
        @DefaultValue("6") int requestsPerMinute,
        @DefaultValue("10") int historyMessages,
        @DefaultValue("150000") int maxSourceChars
) {

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }
}
