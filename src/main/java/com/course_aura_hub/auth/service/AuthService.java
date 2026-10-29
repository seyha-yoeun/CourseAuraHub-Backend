package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.UserResponse;

import java.util.UUID;

/**
 * Service contract for password authentication and account credentials.
 * Registration lives in {@link RegistrationService}; token lifecycle in {@link RefreshSessionService}.
 */
public interface AuthService {

    /**
     * Checks email and password. Returns tokens, or a 2FA challenge when the account requires one.
     */
    LoginOutcome login(LoginRequest request);

    /**
     * Loads the profile of the currently authenticated user.
     */
    UserResponse getCurrentUser(String email);

    /**
     * Changes the password after verifying the current one, signs out every session (including
     * outstanding access tokens) and returns a fresh session for the caller.
     */
    AuthResponse changePassword(UUID userId, String currentPassword, String newPassword);

    /**
     * Signs out every session and revokes access tokens still within their lifetime.
     */
    void logoutAll(UUID userId);
}
