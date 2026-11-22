package com.course_aura_hub.auth.config;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Which Swagger group documents an endpoint, by who may call it. A method's own {@code @PreAuthorize} role rule
 * decides first; otherwise its path and HTTP method are read the way {@link SecurityConfig} authorizes them.
 * Keep the path rules here in step with {@code SecurityConfig} when a role rule changes.
 */
public enum ApiAudience {

    /** Learners and visitors: sign-in, the public catalog and everything under {@code /api/v1/me}. */
    USER,
    /** Instructors (lecturers) and other course staff, who manage their own courses. */
    LECTURER,
    /** Admins only. */
    ADMIN;

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private static final List<String> STAFF_PATHS = List.of(
            "/api/v1/instructor/**", "/api/v1/admin/lessons/*/quiz", "/api/v1/admin/lessons/*/quiz/**");
    private static final List<String> ADMIN_RESOURCES = List.of(
            "/api/v1/admin/**", "/api/v1/students/**", "/api/v1/stats/**", "/api/v1/enrollments/**", "/api/v1/settings/**");
    private static final List<String> CURRICULUM_WRITES = List.of(
            "/api/v1/courses/*/sections/**", "/api/v1/courses/*/lessons/**", "/api/v1/courses/*/curriculum/settings");

    public static ApiAudience of(Method method) {
        String rule = roleRule(method);
        if (rule != null) {
            if (rule.contains("INSTRUCTOR") || rule.contains("@courseAccess")) {
                return LECTURER;
            }
            if (rule.contains("ADMIN")) {
                return ADMIN;
            }
        }
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            return USER;
        }
        RequestMethod httpMethod = mapping.method().length > 0 ? mapping.method()[0] : RequestMethod.GET;
        return ofPath(path(method, mapping), httpMethod);
    }

    static ApiAudience ofPath(String path, RequestMethod httpMethod) {
        if (matchesAny(STAFF_PATHS, path)) {
            return LECTURER;
        }
        if (httpMethod == RequestMethod.GET && PATHS.match("/api/v1/settings/public", path)) {
            return USER;
        }
        if (matchesAny(ADMIN_RESOURCES, path)) {
            return ADMIN;
        }
        boolean write = httpMethod != RequestMethod.GET && httpMethod != RequestMethod.HEAD && httpMethod != RequestMethod.OPTIONS;
        if (write && matchesAny(CURRICULUM_WRITES, path)) {
            return LECTURER;
        }
        if (write && PATHS.match("/api/v1/courses/**", path)) {
            return ADMIN;
        }
        return USER;
    }

    /** The method's {@code @PreAuthorize} expression, or its controller's; null when neither has one. */
    private static String roleRule(Method method) {
        PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (rule == null) {
            rule = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), PreAuthorize.class);
        }
        return rule == null ? null : rule.value();
    }

    private static String path(Method method, RequestMapping mapping) {
        RequestMapping type = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), RequestMapping.class);
        String base = type != null && type.path().length > 0 ? type.path()[0] : "";
        String own = mapping.path().length > 0 ? mapping.path()[0] : "";
        return base + own;
    }

    private static boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> PATHS.match(pattern, path));
    }
}
