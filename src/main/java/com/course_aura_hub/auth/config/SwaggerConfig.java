package com.course_aura_hub.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuthentication";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Course Aura Hub - Authentication API")
                        .description("Production-ready Authentication & Authorization API supporting JWT and OAuth2 (Google, GitHub, Facebook)")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Course Aura Engineering")
                                .email("support@courseaurahub.com"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
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
}
