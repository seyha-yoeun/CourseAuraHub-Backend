package com.course_aura_hub.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI shows the API in three groups, one per role, picked in its "Select a definition" box. Each endpoint
 * is in exactly one group, decided by {@link ApiAudience}.
 */
@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuthentication";
    private static final String TITLE = "Course Aura Hub API";
    private static final String VERSION = "1.0.0";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(info("REST API of the Course Aura Hub learning platform"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your valid JWT Access Token to authorize requests.")));
    }

    @Bean
    public GroupedOpenApi userApi() {
        return group("user", "Learner & Public API", ApiAudience.USER,
                "For learners and visitors: sign-in and accounts, the public catalog, and everything a learner does "
                        + "under /api/v1/me (courses, progress, orders, certificates, notifications).");
    }

    @Bean
    public GroupedOpenApi lecturerApi() {
        return group("lecturer", "Lecturer & Course Staff API", ApiAudience.LECTURER,
                "For instructors (lecturers) and other course staff: their courses, curriculum, quizzes, assignments, "
                        + "live sessions, surveys, earnings and learner questions. Admins can call these too.");
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return group("admin", "Platform Administration API", ApiAudience.ADMIN,
                "Admin only: platform management, course review, orders and refunds, payouts, learners, "
                        + "statistics and settings.");
    }

    private static GroupedOpenApi group(String id, String name, ApiAudience audience, String description) {
        return GroupedOpenApi.builder()
                .group(id)
                .displayName(name)
                .pathsToMatch("/api/**")
                .addOpenApiMethodFilter(method -> ApiAudience.of(method) == audience)
                // A fresh Info per group: the base one is shared, so editing it would change every group
                .addOpenApiCustomizer(api -> api.info(info(description).title(TITLE + " - " + name)))
                .build();
    }

    private static Info info(String description) {
        return new Info()
                .title(TITLE)
                .description(description)
                .version(VERSION)
                .contact(new Contact()
                        .name("Course Aura Engineering")
                        .email("support@courseaurahub.com"))
                .license(new License().name("Apache 2.0").url("https://springdoc.org"));
    }
}
