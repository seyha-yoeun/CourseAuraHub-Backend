package com.course_aura_hub.auth.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String clientIp = getClientIp(request);
        String key = clientIp + ":" + path;

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(path));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}");
        }
    }

    private Bucket createBucket(String path) {
        Bandwidth limit;
        if (path.contains("/api/v1/auth/register")) {
            limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));
        } else if (path.contains("/api/v1/auth/login")) {
            limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        } else if (path.contains("/api/v1/auth/refresh")) {
            limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
        } else if (path.contains("/api/v1/auth/logout")) {
            limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
        } else {
            limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
        }
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/auth/");
    }
}