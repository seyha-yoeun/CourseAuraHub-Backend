package com.course_aura_hub.audit.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuditInterceptorTest {

    @Test
    void resourceIsTheFirstLiteralSegmentAfterTheScope() {
        assertThat(AdminAuditInterceptor.resourceOf("/api/v1/admin/coupons/{id}")).isEqualTo("coupons");
        assertThat(AdminAuditInterceptor.resourceOf("/api/v1/admin/course-reviews/{slug}/approve")).isEqualTo("course-reviews");
        assertThat(AdminAuditInterceptor.resourceOf("/api/v1/instructor/courses/{slug}")).isEqualTo("courses");
        assertThat(AdminAuditInterceptor.resourceOf("/api/v1/courses/{slug}/sections")).isEqualTo("courses");
        assertThat(AdminAuditInterceptor.resourceOf("/api/v1/admin/{id}")).isEqualTo("admin");
    }

    @Test
    void learnerAndAuthEndpointsAreNotAudited() {
        assertThat(AdminAuditInterceptor.isAudited("/api/v1/me/enrollments")).isFalse();
        assertThat(AdminAuditInterceptor.isAudited("/api/v1/me")).isFalse();
        assertThat(AdminAuditInterceptor.isAudited("/api/v1/auth/login")).isFalse();
        assertThat(AdminAuditInterceptor.isAudited("/api/v1/admin/coupons")).isTrue();
        assertThat(AdminAuditInterceptor.isAudited("/api/v1/media")).isTrue();
    }

    @Test
    void pathVariablesAreEscapedJson() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("slug", "a\"b\\c");
        values.put("id", "line\nbreak");
        assertThat(AdminAuditInterceptor.toJson(values)).isEqualTo("{\"slug\":\"a\\\"b\\\\c\",\"id\":\"line\\u000abreak\"}");
    }
}
