package com.course_aura_hub.assistant.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {

    /** Only created with an API key; without one the assistant answers 503. */
    @Bean
    @ConditionalOnExpression("'${app.assistant.api-key:}' != ''")
    public AnthropicClient anthropicClient(AssistantProperties properties) {
        return AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build();
    }
}
