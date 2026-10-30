package com.course_aura_hub.auth.config;

import com.course_aura_hub.auth.security.CustomUserDetailsService;
import com.course_aura_hub.auth.security.JwtAuthFilter;
import com.course_aura_hub.auth.security.OAuth2AuthenticationFailureHandler;
import com.course_aura_hub.auth.security.OAuth2AuthenticationSuccessHandler;
import com.course_aura_hub.auth.security.oauth2.ProviderEmailEnrichingOAuth2UserService;
import com.course_aura_hub.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CookieProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    /** Unauthenticated auth endpoints. They don't rely on session cookies, so CSRF protection is not applicable. */
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/login",
            "/api/v1/auth/mfa/verify",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    };

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomUserDetailsService customUserDetailsService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final ProviderEmailEnrichingOAuth2UserService oAuth2UserService;
    private final RateLimitingFilter rateLimitingFilter;
    private final PasswordEncoder passwordEncoder;
    private final CookieProperties cookieProperties;
    private final CorsProperties corsProperties;

    // Self-contained Jackson ObjectMapper instance to avoid early initialization circularities
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF configuration with double-submit cookie pattern
        CsrfTokenRequestAttributeHandler csrfTokenHandler = new CsrfTokenRequestAttributeHandler();
        csrfTokenHandler.setCsrfRequestAttributeName("_csrf");

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfTokenHandler)
                        .ignoringRequestMatchers(PUBLIC_AUTH_ENDPOINTS)
                        .ignoringRequestMatchers("/oauth2/**", "/login/oauth2/**")
                        .ignoringRequestMatchers("/api/v1/payments/webhooks/*")
                        .ignoringRequestMatchers(bearerOnlyRequest())
                )
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public Auth Endpoints & OAuth2 Authorization Endpoints
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/oauth2/**",
                                "/api/v1/oauth2/**",
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()
                        // Learner self-service (my courses, profile, wishlist, progress, reviews, achievements)
                        .requestMatchers("/api/v1/me", "/api/v1/me/**").authenticated()
                        // Public certificate verification (numbers are unguessable; no contact details exposed)
                        .requestMatchers(HttpMethod.GET, "/api/v1/certificates/*").permitAll()
                        // Payment provider webhooks, authenticated by signature in the gateway
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/*").permitAll()
                        // Account administration
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Client-safe platform branding
                        .requestMatchers(HttpMethod.GET, "/api/v1/settings/public").authenticated()
                        // Admin-only management resources
                        .requestMatchers(
                                "/api/v1/students",
                                "/api/v1/students/**",
                                "/api/v1/stats",
                                "/api/v1/stats/**",
                                "/api/v1/enrollments",
                                "/api/v1/enrollments/**",
                                "/api/v1/settings",
                                "/api/v1/settings/**"
                        ).hasRole("ADMIN")
                        // Course writes are admin-only, reads are available to all authenticated users
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/courses/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/courses/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/courses/**").authenticated()
                        // Swagger & OpenAPI documentation
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // Default error controller
                        .requestMatchers("/error").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                // Spring Security OAuth2 Login configuration with redirect handlers
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint.baseUri("/oauth2/authorization"))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/login/oauth2/code/*"))
                        .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                )
                .authenticationProvider(authenticationProvider(customUserDetailsService))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter, JwtAuthFilter.class);

        return http.build();
    }

    /**
     * Requests that authenticate only with an Authorization header cannot be forged cross-site
     * (browsers don't attach it automatically, and CORS limits who may set it), so they skip CSRF.
     * As soon as an auth cookie is present the CSRF token is required.
     */
    private RequestMatcher bearerOnlyRequest() {
        return request -> {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            return authorization != null && authorization.startsWith("Bearer ") && !hasAuthCookie(request);
        };
    }

    private boolean hasAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(cookieProperties.accessTokenName())
                    || cookie.getName().equals(cookieProperties.refreshTokenName())) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return this.objectMapper;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Explicit trusted origins only: credentials are allowed, so a wildcard would let any site read responses
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-CSRF-TOKEN", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Authorization", "X-CSRF-TOKEN", "X-XSRF-TOKEN", "Retry-After"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> apiResponse = ApiResponse.error("Full authentication is required to access this resource");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        };
    }

    @Bean
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> apiResponse = ApiResponse.error("Access denied: You do not have permission to access this resource");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        };
    }
}
