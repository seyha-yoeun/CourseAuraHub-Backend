package com.course_aura_hub.chat.service;

import com.course_aura_hub.auth.config.RateLimitProperties;
import com.course_aura_hub.common.exception.BusinessRuleException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-user request rates for chat (messages, member searches), per instance. Daily caps such as new groups per
 * day are counted in the database instead, so they hold across instances. Off when rate limiting is turned off.
 */
@Component
@RequiredArgsConstructor
public class ChatRateLimiter {

    private static final int MAX_TRACKED = 20_000;

    private final RateLimitProperties rateLimitProperties;

    private final Map<String, Bucket> buckets = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    /** Refuses the request when this user went over {@code perMinute} for this kind of action. */
    public void check(String action, UUID userId, int perMinute, String message) {
        if (!rateLimitProperties.enabled()) {
            return;
        }
        Bucket bucket;
        synchronized (buckets) {
            bucket = buckets.computeIfAbsent(action + ":" + userId, ignored -> Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(perMinute)
                            .refillGreedy(perMinute, Duration.ofMinutes(1))
                            .build())
                    .build());
        }
        if (!bucket.tryConsume(1)) {
            throw new BusinessRuleException(message);
        }
    }

    public boolean enabled() {
        return rateLimitProperties.enabled();
    }
}
