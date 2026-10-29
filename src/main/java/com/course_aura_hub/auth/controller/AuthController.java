package com.course_aura_hub.auth.controller;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.ChangePasswordRequest;
import com.course_aura_hub.auth.dto.EmailRequest;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.MfaChallengeResponse;
import com.course_aura_hub.auth.dto.MfaVerifyRequest;
import com.course_aura_hub.auth.dto.RefreshTokenRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.dto.ResetPasswordRequest;
import com.course_aura_hub.auth.dto.UserResponse;
import com.course_aura_hub.auth.dto.VerificationRequiredResponse;
import com.course_aura_hub.auth.dto.VerifyEmailRequest;
import com.course_aura_hub.auth.facade.AuthFacade;
import com.course_aura_hub.auth.security.AuthCookieService;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.auth.service.LoginOutcome;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @Operation(summary = "Register new user", description = "Creates a pending local account and emails a one-time verification code. "
            + "No tokens are issued until the code is verified via POST /verify-email. The response is identical for new and already-registered emails.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Verification required", content = @Content(schema = @Schema(implementation = VerificationRequiredResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload or validation failed"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Verification email could not be sent; retry later")})
    public ResponseEntity<ApiResponse<VerificationRequiredResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received registration request");
        VerificationRequiredResponse body = authFacade.register(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Check your email for a verification code to finish creating your account", body));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with one-time code", description = "Confirms the emailed code, activates the account and signs the user in. "
            + "Returns JWT tokens in the body and sets them as HttpOnly cookies.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified; signed in", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid, expired, used or exhausted code")})
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authFacade.verifyEmail(request.email(), request.code());
        authCookieService.setAuthCookies(response, authResponse);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", authResponse));
    }

    @PostMapping("/verify-email/resend")
    @Operation(summary = "Resend verification code", description = "Sends a new code (superseding the previous one) if the account is pending. "
            + "Subject to a cooldown and an hourly cap. Always returns the same response.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Request accepted", content = @Content(schema = @Schema(implementation = VerificationRequiredResponse.class)))})
    public ResponseEntity<ApiResponse<VerificationRequiredResponse>> resendVerification(@Valid @RequestBody EmailRequest request) {
        VerificationRequiredResponse body = authFacade.resendVerificationCode(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("If the account is awaiting verification, a new code has been sent", body));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Emails a single-use reset link if the account is eligible. Always returns the same response.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Request accepted")})
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody EmailRequest request) {
        authFacade.requestPasswordReset(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("If an account exists for that email, a password reset link has been sent", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Sets a new password using the emailed token. Signs out every session; the user must sign in again.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid, expired or used token, or weak password")})
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletResponse response) {
        authFacade.resetPassword(request);
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success("Your password has been reset. Please sign in with your new password.", null));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Authenticates local user credentials. Returns JWT Access & Refresh tokens in the response body and sets them as HttpOnly cookies. "
            + "When two-factor authentication is enabled, returns an MfaChallengeResponse instead and no tokens.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful, or second factor required", content = @Content(schema = @Schema(oneOf = {AuthResponse.class, MfaChallengeResponse.class}))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials or temporarily locked"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Email not verified or account disabled")})
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("Received login request");
        return switch (authFacade.login(request)) {
            case LoginOutcome.Authenticated authenticated -> {
                authCookieService.setAuthCookies(response, authenticated.auth());
                yield ResponseEntity.ok(ApiResponse.success("Authentication successful", authenticated.auth()));
            }
            case LoginOutcome.MfaRequired mfa ->
                    ResponseEntity.ok(ApiResponse.success("Two-factor authentication required", mfa.challenge()));
        };
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Complete two-factor login", description = "Submits an authenticator or recovery code for a pending login challenge. "
            + "The challenge comes from the login response, or from the mfa_challenge cookie after OAuth2 sign-in.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Signed in", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid code or expired challenge")})
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String challenge = StringUtils.hasText(body.mfaToken()) ? body.mfaToken() : authCookieService.getMfaChallenge(request);
        AuthResponse authResponse = authFacade.completeMfaLogin(challenge, body.code(), body.recoveryCode());
        authCookieService.clearMfaChallengeCookie(response);
        authCookieService.setAuthCookies(response, authResponse);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", authResponse));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates new access and refresh tokens using a refresh token supplied in the request body or in the refresh-token cookie. "
            + "Implements rotation; replaying a rotated token revokes that session.")
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
    @Operation(summary = "Logout user", description = "Signs out the current session only (identified by the refresh token in the body or cookie, "
            + "or by the access token) and clears auth cookies. Other devices stay signed in.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout successful")})
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest body,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("Received logout request");
        String refreshToken = resolveRefreshToken(body, request);
        authFacade.logout(
                refreshToken,
                principal != null ? principal.getUserId() : null,
                principal != null ? principal.getSessionId() : null
        );
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Logout from all devices", description = "Revokes every session and every outstanding access token of the current user.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All sessions revoked"), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")})
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal AuthUserPrincipal principal, HttpServletResponse response) {
        authFacade.logoutAll(principal.getUserId());
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success("Signed out of all sessions", null));
    }

    @PostMapping("/password/change")
    @Operation(summary = "Change password", description = "Changes the password of the signed-in account, signs out all other sessions and returns a fresh session.")
    @ApiResponses(value = {@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed", content = @Content(schema = @Schema(implementation = AuthResponse.class))), @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Wrong current password or weak new password")})
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authFacade.changePassword(principal.getUserId(), request);
        authCookieService.setAuthCookies(response, authResponse);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", authResponse));
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
