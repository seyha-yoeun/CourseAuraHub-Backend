package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.config.JwtProperties;
import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.exception.AuthException;
import com.course_aura_hub.auth.exception.InvalidCredentialsException;
import com.course_aura_hub.auth.exception.TokenExpiredException;
import com.course_aura_hub.auth.exception.UserAlreadyExistsException;
import com.course_aura_hub.auth.mapper.UserMapper;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;

    private static final long REMEMBER_ME_REFRESH_TOKEN_EXPIRATION = 30L * 24 * 60 * 60 * 1000; // 30 days in milliseconds

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("An account with email " + request.email() + " already exists");
        }

        // Map DTO to User entity via MapStruct
        User user = userMapper.toEntity(request);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));

        User savedUser = userRepository.save(user);
        log.info("Successfully registered local user with email: {}", savedUser.getEmail());

        return createAuthResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new InvalidCredentialsException(
                    "Account was created using " + user.getProvider() + ". Please sign in with OAuth provider."
            );
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            long minutesRemaining = Duration.between(Instant.now(), user.getLockedUntil()).toMinutes() + 1;
            throw new InvalidCredentialsException("Account is temporarily locked. Try again in " + minutesRemaining + " minutes.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (BadCredentialsException ex) {
            handleFailedLogin(user);
            log.warn("Invalid password attempt for email: {}", normalizedEmail);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        handleSuccessfulLogin(user);
        log.info("Successfully authenticated user: {}", normalizedEmail);
        return createAuthResponse(user, request.rememberMe());
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= 5) {
            user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            log.warn("Account locked for 15 minutes due to failed login attempts: {}", user.getEmail());
        }

        userRepository.save(user);
    }

    private void handleSuccessfulLogin(User user) {
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException("Refresh token is required");
        }

        if (jwtTokenProvider.isTokenExpired(refreshToken)) {
            throw new TokenExpiredException("Refresh token has expired. Please login again.");
        }

        Claims claims;
        try {
            claims = jwtTokenProvider.extractAllClaims(refreshToken);
        } catch (Exception ex) {
            throw new AuthException("Invalid refresh token: " + ex.getMessage());
        }

        String tokenType = claims.get("tokenType", String.class);
        if (!"REFRESH".equalsIgnoreCase(tokenType)) {
            throw new AuthException("Provided token is not a valid refresh token");
        }

        String email = claims.getSubject();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("User not found for token subject"));

        if (!user.isEnabled()) {
            throw new AuthException("User account is disabled");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AuthException("User account is temporarily locked");
        }

        String storedRefreshToken = user.getRefreshToken();
        if (storedRefreshToken == null || !jwtTokenProvider.matchesRefreshToken(refreshToken, storedRefreshToken)) {
            log.warn("Refresh token mismatch for user: {}", email);
            throw new AuthException("Invalid refresh token");
        }

        // Token rotation: invalidate old refresh token and issue new pair
        return createAuthResponse(user);
    }

@Override
    @Transactional
    public AuthResponse createAuthResponse(User user) {
        return createAuthResponse(user, false);
    }

    @Transactional
    public AuthResponse createAuthResponse(User user, boolean rememberMe) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        long refreshExpiration = rememberMe ? REMEMBER_ME_REFRESH_TOKEN_EXPIRATION : jwtProperties.refreshTokenExpiration();
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, refreshExpiration);
        long expiresIn = jwtTokenProvider.getAccessTokenExpiration();

        user.setAccessToken(accessToken);
        user.setRefreshToken(jwtTokenProvider.hashRefreshToken(refreshToken));
        user.setTokensIssuedAt(Instant.now());
        user.setRefreshTokenExpiresAt(
                Instant.now().plusMillis(refreshExpiration)
        );

        userRepository.save(user);

        return userMapper.toAuthResponse(
                user,
                accessToken,
                refreshToken,
                expiresIn
        );
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Claims claims = jwtTokenProvider.extractAllClaims(refreshToken);
            String tokenType = claims.get("tokenType", String.class);
            if (!"REFRESH".equalsIgnoreCase(tokenType)) {
                return;
            }

            String email = claims.getSubject();
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                user.setAccessToken(null);
                user.setRefreshToken(null);
                user.setRefreshTokenExpiresAt(null);
                user.setTokensIssuedAt(null);
                userRepository.save(user);
                log.info("Successfully revoked tokens for user: {}", email);
            }
        } catch (Exception ex) {
            log.debug("Logout with invalid token: {}", ex.getMessage());
        }
    }
}