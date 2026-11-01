package com.course_aura_hub.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes one line per request and tags every log line of that request with a request id.
 *
 * <p>Runs before Spring Security, so requests it refuses are logged too. Only the method, path,
 * status and duration are recorded: never the query string, headers, cookies or bodies, because
 * those can carry tokens and OAuth codes.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String REQUEST_ID_KEY = "requestId";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final int MAX_PATH_LENGTH = 200;
    private static final List<String> QUIET_PREFIXES = List.of("/swagger-ui", "/v3/api-docs", "/favicon.ico", "/error");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return QUIET_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_KEY, requestId);

        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failed = true;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            // The container hasn't turned an escaping exception into a status yet
            int status = failed ? HttpStatus.INTERNAL_SERVER_ERROR.value() : response.getStatus();
            logRequest(request.getMethod(), safePath(request), status, durationMs);
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    private void logRequest(String method, String path, int status, long durationMs) {
        log.atLevel(levelFor(status))
                .addKeyValue("http_method", method)
                .addKeyValue("http_path", path)
                .addKeyValue("http_status", status)
                .addKeyValue("duration_ms", durationMs)
                .log("{} {} {} {}ms", method, path, status, durationMs);
    }

    private Level levelFor(int status) {
        if (status >= 500) {
            return Level.ERROR;
        }
        return status >= 400 ? Level.WARN : Level.INFO;
    }

    /** A caller's id is kept only in a safe shape, so a crafted header can't forge log lines. */
    private String resolveRequestId(HttpServletRequest request) {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        if (supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private String safePath(HttpServletRequest request) {
        String path = request.getRequestURI().replaceAll("[\\r\\n]", "");
        return path.length() <= MAX_PATH_LENGTH ? path : path.substring(0, MAX_PATH_LENGTH);
    }
}
