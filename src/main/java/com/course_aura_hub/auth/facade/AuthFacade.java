package com.course_aura_hub.auth.facade;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.dto.RegisterRequest;

/**
 * Facade pattern interface providing a single, unified entry point for all authentication,
 * registration, token refresh, and OAuth2 delegation operations.
 */
public interface AuthFacade {

    /**
     * Registers a new user with local email and password.
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Authenticates a user with email and password credentials.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Issues a refreshed access token and refresh token from a valid refresh token.
     */
    AuthResponse refreshToken(String refreshToken);

    /**
     * Revokes all tokens for a user (logout).
     */
    void logout(String refreshToken);

    /**
     * Processes OAuth2 user data and issues JWT tokens.
     */
    AuthResponse processOAuth2User(OAuth2UserDto oAuth2UserDto);

    /**
     * Handles provider OAuth2 callback exchange and user provisioning.
     */
    AuthResponse processOAuth2Callback(String provider, String code, String state);

}