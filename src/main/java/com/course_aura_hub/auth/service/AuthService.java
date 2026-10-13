package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.model.User;

/**
 * Service contract defining operations for local registration, authentication,
 * and JWT token lifecycle management.
 */
public interface AuthService {

    /**
     * Registers a new local user with encrypted password.
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates an existing user and returns JWT token pair.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Generates a fresh access and refresh token pair using an existing valid refresh token.
     * Implements token rotation: the old refresh token is invalidated.
     */
    AuthResponse refreshToken(String refreshToken);

    /**
     * Revokes all tokens for a user (logout).
     */
    void logout(String refreshToken);

    /**
     * Builds standard AuthResponse with freshly minted JWT tokens for a given user.
     */
    AuthResponse createAuthResponse(User user);

    /**
     * Builds AuthResponse with optional remember-me extended refresh token expiry.
     */
    AuthResponse createAuthResponse(User user, boolean rememberMe);
}