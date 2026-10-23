package com.course_aura_hub.auth.controller;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.RefreshTokenRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.dto.UserResponse;
import com.course_aura_hub.auth.facade.AuthFacade;
import com.course_aura_hub.auth.security.AuthCookieService;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration, login, token refresh, and OAuth2 authentication")
public class AuthController {

    private final AuthFacade authFacade;
    private final AuthCookieService authCookieService;

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Registers a new user locally using email and password. Returns JWT tokens in the response body and sets them as HttpOnly cookies.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User successfully registered", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload or validation failed"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User with given email already exists")})
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        log.info("Received registration request for email: {}", request.email());
        AuthResponse authResponse = authFacade.register(request);
        authCookieService.setAuthCookies(response, authResponse);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", authResponse));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Authenticates local user credentials. Returns JWT Access & Refresh tokens in the response body and sets them as HttpOnly cookies.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid email or password credentials")})
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("Received login request for email: {}", request.email());
        AuthResponse authResponse = authFacade.login(request);
        authCookieService.setAuthCookies(response, authResponse, request.rememberMe());
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", authResponse));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates new access and refresh tokens using a refresh token supplied in the request body or in the refresh-token cookie. Implements token rotation.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh token expired or invalid")})
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("Received token refresh request");
        String refreshToken = resolveRefreshToken(body, request);
        AuthResponse authResponse = authFacade.refreshToken(refreshToken);
        authCookieService.setAuthCookies(response, authResponse);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResponse));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes the refresh token supplied in the request body or cookie and clears auth cookies.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful")})
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("Received logout request");
        String refreshToken = resolveRefreshToken(body, request);
        if (StringUtils.hasText(refreshToken)) {
            authFacade.logout(refreshToken);
        }
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user", description = "Returns the profile of the currently authenticated user. Requires a valid Bearer access token or access-token cookie.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current user info", content = @Content(schema = @Schema(implementation = UserResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")})
    public ResponseEntity<ApiResponse<UserResponse>> me(Authentication authentication) {
        UserResponse user = authFacade.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Current user retrieved", user));
    }

    @GetMapping("/oauth2/callback/{provider}")
    @Operation(summary = "OAuth2 authorization callback", description = "Processes OAuth2 provider callback code for Google, GitHub, or Facebook")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OAuth2 authentication successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing code or unsupported provider")})
    public ResponseEntity<ApiResponse<AuthResponse>> oauth2Callback(@Parameter(description = "OAuth2 provider name (google, github, facebook)", example = "google") @PathVariable String provider, @Parameter(description = "Authorization code issued by OAuth2 provider") @RequestParam(required = false) String code, @Parameter(description = "State token for CSRF mitigation") @RequestParam(required = false) String state) {
        log.info("Received OAuth2 callback for provider: {}", provider);
        AuthResponse response = authFacade.processOAuth2Callback(provider, code, state);
        return ResponseEntity.ok(ApiResponse.success("OAuth2 authentication successful", response));
    }

    @GetMapping("/oauth2/authorization/{provider}")
    @Operation(summary = "Initiate OAuth2 authorization", description = "Redirects to the OAuth2 provider's authorization endpoint")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirect to OAuth2 provider"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported provider")})
    public void oauth2Authorization(@Parameter(description = "OAuth2 provider name (google, github, facebook)", example = "google") @PathVariable String provider, HttpServletResponse response) throws IOException {
        log.info("Initiating OAuth2 authorization for provider: {}", provider);
        String authorizationUrl = "/oauth2/authorization/" + provider.toLowerCase();
        response.sendRedirect(authorizationUrl);
    }

    @GetMapping("/oauth2/google")
    @Operation(summary = "Initiate Google OAuth2 authorization", description = "Redirects to Google's OAuth2 authorization endpoint")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirect to Google OAuth2")})
    public void oauth2Google(HttpServletResponse response) throws IOException {
        log.info("Initiating Google OAuth2 authorization");
        response.sendRedirect("/oauth2/authorization/google");
    }

    @GetMapping("/oauth2/github")
    @Operation(summary = "Initiate GitHub OAuth2 authorization", description = "Redirects to GitHub's OAuth2 authorization endpoint")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirect to GitHub OAuth2")})
    public void oauth2Github(HttpServletResponse response) throws IOException {
        log.info("Initiating GitHub OAuth2 authorization");
        response.sendRedirect("/oauth2/authorization/github");
    }

    @GetMapping("/oauth2/facebook")
    @Operation(summary = "Initiate Facebook OAuth2 authorization", description = "Redirects to Facebook's OAuth2 authorization endpoint")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirect to Facebook OAuth2")})
    public void oauth2Facebook(HttpServletResponse response) throws IOException {
        log.info("Initiating Facebook OAuth2 authorization");
        response.sendRedirect("/oauth2/authorization/facebook");
    }

    private String resolveRefreshToken(RefreshTokenRequest body, HttpServletRequest request) {
        if (body != null && StringUtils.hasText(body.refreshToken())) {
            return body.refreshToken();
        }
        return authCookieService.getRefreshToken(request);
    }
}
