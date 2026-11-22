package com.course_aura_hub.auth.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Every endpoint lands in the Swagger group of the role that may call it. */
class ApiAudienceTest {

    private static final Map<String, ApiAudience> ENDPOINTS = new TreeMap<>();

    @BeforeAll
    static void classifyEveryEndpoint() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var candidate : scanner.findCandidateComponents("com.course_aura_hub")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String prefix = base != null && base.path().length > 0 ? base.path()[0] : "";
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String verb = mapping.method().length > 0 ? mapping.method()[0].name() : "GET";
                String path = prefix + (mapping.path().length > 0 ? mapping.path()[0] : "");
                ENDPOINTS.put(verb + " " + path, ApiAudience.of(method));
            }
        }
        // The full list, for reviewing the grouping by eye
        Files.writeString(Path.of("target", "api-audience.txt"), ENDPOINTS.entrySet().stream()
                .map(entry -> entry.getValue() + "\t" + entry.getKey())
                .sorted()
                .collect(Collectors.joining("\n")));
    }

    private static ApiAudience audience(String endpoint) {
        assertThat(ENDPOINTS).as("endpoint exists").containsKey(endpoint);
        return ENDPOINTS.get(endpoint);
    }

    @Test
    void learnersAndVisitors_areUsers() {
        assertThat(audience("POST /api/v1/auth/login")).isEqualTo(ApiAudience.USER);
        assertThat(audience("GET /api/v1/me/courses")).isEqualTo(ApiAudience.USER);
        assertThat(audience("GET /api/v1/courses/{slug}")).isEqualTo(ApiAudience.USER);
        assertThat(audience("GET /api/v1/settings/public")).isEqualTo(ApiAudience.USER);
        assertThat(audience("GET /api/v1/courses/{slug}/changelog")).isEqualTo(ApiAudience.USER);
    }

    @Test
    void courseStaffEndpoints_areLecturers() {
        assertThat(audience("GET /api/v1/instructor/earnings")).isEqualTo(ApiAudience.LECTURER);
        assertThat(audience("POST /api/v1/courses/{slug}/sections")).isEqualTo(ApiAudience.LECTURER);
        assertThat(audience("PUT /api/v1/courses/{slug}/lessons/{lessonId}/captions/{lang}")).isEqualTo(ApiAudience.LECTURER);
        assertThat(audience("GET /api/v1/admin/lessons/{lessonId}/quiz")).isEqualTo(ApiAudience.LECTURER);
    }

    @Test
    void adminOnlyEndpoints_areAdmin() {
        assertThat(audience("POST /api/v1/courses")).isEqualTo(ApiAudience.ADMIN);
        assertThat(audience("DELETE /api/v1/courses/{slug}")).isEqualTo(ApiAudience.ADMIN);
        assertThat(audience("GET /api/v1/admin/payouts")).isEqualTo(ApiAudience.ADMIN);
        assertThat(audience("GET /api/v1/stats/funnel")).isEqualTo(ApiAudience.ADMIN);
        assertThat(audience("GET /api/v1/students")).isEqualTo(ApiAudience.ADMIN);
    }

    @Test
    void everyEndpointHasAGroup_andAllThreeAreUsed() {
        assertThat(ENDPOINTS).hasSizeGreaterThan(250);
        assertThat(ENDPOINTS.values()).contains(ApiAudience.USER, ApiAudience.LECTURER, ApiAudience.ADMIN);
    }
}
