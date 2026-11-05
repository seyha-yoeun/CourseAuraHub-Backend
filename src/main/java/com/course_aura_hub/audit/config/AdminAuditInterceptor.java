package com.course_aura_hub.audit.config;

import com.course_aura_hub.audit.model.AdminAuditEvent;
import com.course_aura_hub.audit.model.AuditOutcome;
import com.course_aura_hub.audit.service.AdminAuditService;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.auth.security.ClientInfoResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Records every write request handled for an admin or instructor: who, which handler, which path and resource,
 * and how it ended. Learner self-service under /me and the auth endpoints (audited by AuthAuditService) are
 * skipped. Bodies, query strings and headers are never read.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String API_PREFIX = "/api/v1/";
    private static final Set<String> SCOPES = Set.of("admin", "instructor");
    private static final int MAX_VALUE_LENGTH = 200;

    private final AdminAuditService auditService;
    private final ClientInfoResolver clientInfoResolver;

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex
    ) {
        if (!WRITE_METHODS.contains(request.getMethod()) || !(handler instanceof HandlerMethod method)) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserPrincipal principal)) {
            return;
        }
        String role = staffRole(authentication);
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (role == null || pattern == null || !isAudited(pattern)) {
            return;
        }

        try {
            int status = statusOf(response, ex);
            Map<String, String> variables = pathVariables(request);
            auditService.record(AdminAuditEvent.builder()
                    .actorId(principal.getUserId())
                    .actorEmail(principal.getUsername())
                    .actorRole(role)
                    .action(method.getBeanType().getSimpleName() + "." + method.getMethod().getName())
                    .httpMethod(request.getMethod())
                    .pathPattern(pattern)
                    .resource(resourceOf(pattern))
                    .resourceId(variables.values().stream().findFirst().orElse(null))
                    .pathVariables(variables.isEmpty() ? null : toJson(variables))
                    .status(status)
                    .outcome(status < 400 ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE)
                    .requestId(MDC.get("requestId"))
                    .ipAddress(clientInfoResolver.resolveClientIp(request))
                    .build());
        } catch (RuntimeException failure) {
            log.error("Could not build admin audit event path={}", pattern, failure);
        }
    }

    private static String staffRole(Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
        if (authorities.contains("ROLE_ADMIN")) {
            return "ADMIN";
        }
        return authorities.contains("ROLE_INSTRUCTOR") ? "INSTRUCTOR" : null;
    }

    static boolean isAudited(String pattern) {
        return pattern.startsWith(API_PREFIX)
                && !pattern.startsWith(API_PREFIX + "auth")
                && !pattern.startsWith(API_PREFIX + "me/")
                && !pattern.equals(API_PREFIX + "me");
    }

    /** The first literal segment after /api/v1 and an optional admin/instructor scope, such as "coupons". */
    static String resourceOf(String pattern) {
        String[] segments = pattern.substring(API_PREFIX.length()).split("/");
        int index = segments.length > 1 && SCOPES.contains(segments[0]) ? 1 : 0;
        String segment = segments[index];
        return segment.startsWith("{") ? segments[0] : segment;
    }

    private static int statusOf(HttpServletResponse response, Exception ex) {
        if (ex instanceof AccessDeniedException) {
            return HttpStatus.FORBIDDEN.value();
        }
        // An exception that no handler resolved hasn't been turned into a status yet
        return ex != null && response.getStatus() < 400 ? HttpStatus.INTERNAL_SERVER_ERROR.value() : response.getStatus();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    static String toJson(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> quote(entry.getKey()) + ":" + quote(truncate(entry.getValue())))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH);
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
