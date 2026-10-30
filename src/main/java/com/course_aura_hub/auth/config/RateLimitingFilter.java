package com.course_aura_hub.auth.config;

import com.course_aura_hub.auth.security.ClientInfoResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Per-client token-bucket limits for '/api/v1/auth/**'.
 *
 * <p>Clients are identified by {@link ClientInfoResolver}, which ignores spoofed X-Forwarded-For
 * headers. Buckets are keyed by rule name (not raw path, so random URLs can't create keys) and
 * held in a bounded LRU map. Limits are per instance; a multi-instance deployment should back
 * this with a shared store (e.g. Bucket4j + Redis).</p>
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String DEFAULT_RULE = "default";
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String TOO_MANY_REQUESTS_BODY =
            "{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}";

    private final RateLimitProperties properties;
    private final ClientInfoResolver clientInfoResolver;
    private final Map<String, RateLimitProperties.Rule> rulesByPath = new LinkedHashMap<>();
    private final Map<String, Bucket> buckets;

    public RateLimitingFilter(RateLimitProperties properties, ClientInfoResolver clientInfoResolver) {
        this.properties = properties;
        this.clientInfoResolver = clientInfoResolver;
        properties.rules().values().forEach(rule -> rulesByPath.put(normalize(rule.path()), rule));
        int maxEntries = properties.maxTrackedClients();
        this.buckets = new LinkedHashMap<>(1024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = normalize(request.getRequestURI());
        RateLimitProperties.Rule rule = rulesByPath.getOrDefault(path, properties.defaultRule());
        String ruleKey = rulesByPath.containsKey(path) ? path : DEFAULT_RULE;
        String key = clientInfoResolver.resolveClientIp(request) + "|" + ruleKey;

        ConsumptionProbe probe = bucketFor(key, rule).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        log.warn("Rate limit exceeded rule={}", ruleKey);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(TOO_MANY_REQUESTS_BODY);
    }

    int trackedClientCount() {
        synchronized (buckets) {
            return buckets.size();
        }
    }

    private Bucket bucketFor(String key, RateLimitProperties.Rule rule) {
        synchronized (buckets) {
            return buckets.computeIfAbsent(key, k -> Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(rule.capacity())
                            .refillGreedy(rule.capacity(), rule.period())
                            .build())
                    .build());
        }
    }

    private static String normalize(String path) {
        if (path == null) {
            return "";
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        return !request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }
}
