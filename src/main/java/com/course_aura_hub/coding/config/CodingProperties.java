package com.course_aura_hub.coding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;

/**
 * Coding exercises run learners' code in a Judge0 sandbox (https://judge0.com), never on this server. Leave the URL
 * empty to turn running off; exercises can still be written and read.
 *
 * @param judge0Url     base URL of a Judge0 CE server you run or subscribe to
 * @param authHeader    header carrying the token, such as X-Auth-Token, or X-RapidAPI-Key for RapidAPI
 * @param authToken     the token; blank sends none
 * @param languages     language name to Judge0 language id
 * @param runsPerMinute runs one learner may start per minute
 * @param maxTests      most tests an exercise may have, examples included
 * @param maxCodeBytes  largest submission
 * @param timeout       longest wait for Judge0 per test
 */
@ConfigurationProperties(prefix = "app.coding")
public record CodingProperties(
        @DefaultValue("") String judge0Url,
        @DefaultValue("X-Auth-Token") String authHeader,
        @DefaultValue("") String authToken,
        Map<String, Integer> languages,
        @DefaultValue("10") int runsPerMinute,
        @DefaultValue("20") int maxTests,
        @DefaultValue("65536") int maxCodeBytes,
        @DefaultValue("20s") Duration timeout
) {

    /** Judge0 CE language ids; override or extend with app.coding.languages.<name>=<id>. */
    private static final Map<String, Integer> DEFAULT_LANGUAGES = Map.of(
            "python", 71, "javascript", 63, "java", 62, "cpp", 54, "c", 50, "go", 60, "typescript", 74);

    public CodingProperties {
        languages = languages == null || languages.isEmpty() ? DEFAULT_LANGUAGES : Map.copyOf(languages);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(judge0Url);
    }
}
