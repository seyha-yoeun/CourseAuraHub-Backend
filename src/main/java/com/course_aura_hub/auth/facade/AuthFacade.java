package com.course_aura_hub.auth.facade;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.ChangePasswordRequest;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.dto.ResetPasswordRequest;
import com.course_aura_hub.auth.dto.UserResponse;
import com.course_aura_hub.auth.dto.VerificationRequiredResponse;
import com.course_aura_hub.auth.service.LoginOutcome;

import java.util.UUID;

/**
 * Facade pattern interface providing a single, unified entry point for all authentication,
 * registration, token refresh, and OAuth2 delegation operations.
 *
 * <p>Emails are sent here, after the underlying service transaction has committed.</p>
 */
public interface AuthFacade {

    /**
     * Starts a local registration; the account stays pending until the emailed code is verified.
     * The response is identical whether or not the email already has an account.
     */
    VerificationRequiredResponse register(RegisterRequest request);

    /**
     * Confirms the emailed code, activates the account and signs the user in.
     */
    AuthResponse verifyEmail(String email, String code);

    /**
     * Sends a new verification code when the account is pending and not throttled. Always succeeds.
     */
    VerificationRequiredResponse resendVerificationCode(String email);

    /**
     * Emails a reset link when the account is eligible. Always succeeds.
     */
    void requestPasswordReset(String email);

    /**
     * Completes a password reset and signs out every session.
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Authenticates a user with email and password credentials.
     */
    LoginOutcome login(LoginRequest request);

    /**
     * Completes a two-factor login challenge.
     */
    AuthResponse completeMfaLogin(String challengeToken, String code, String recoveryCode);

    /**
     * Issues a refreshed access token and refresh token from a valid refresh token.
     */
    AuthResponse refreshToken(String refreshToken);

    /**
     * Signs out one session: the one owning the refresh token, otherwise the caller's current session.
     */
    void logout(String refreshToken, UUID userId, UUID currentSessionId);

    /**
     * Signs out every session of the user and revokes outstanding access tokens.
     */
    void logoutAll(UUID userId);

    /**
     * Loads the profile of the currently authenticated user.
     */
    UserResponse getCurrentUser(String email);

    /**
     * Changes the password, signs out other sessions and returns a new session for the caller.
     */
    AuthResponse changePassword(UUID userId, ChangePasswordRequest request);

    /**
     * Handles provider OAuth2 callback exchange and user provisioning.
     */
    AuthResponse processOAuth2Callback(String provider, String code, String state);
}
