package com.course_aura_hub.auth.controller;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.RefreshTokenRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.facade.AuthFacade;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Registers a new user locally using email and password, returning JWT tokens")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User successfully registered", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload or validation failed"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User with given email already exists")})
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received registration request for email: {}", request.email());
        AuthResponse response = authFacade.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Authenticates local user credentials and returns JWT Access & Refresh tokens")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid email or password credentials")})
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received login request for email: {}", request.email());
        AuthResponse response = authFacade.login(request);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates a new access token using a valid refresh token")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh token expired or invalid")})
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Received token refresh request");
        AuthResponse response = authFacade.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes all tokens for the user")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid refresh token")})
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Received logout request");
        authFacade.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
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
    public void oauth2Authorization(@Parameter(description = "OAuth2 provider name (google, github, facebook)", example = "google") @PathVariable String provider, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        log.info("Initiating OAuth2 authorization for provider: {}", provider);
        String authorizationUrl = "/oauth2/authorization/" + provider.toLowerCase();
        response.sendRedirect(authorizationUrl);
    }
}
