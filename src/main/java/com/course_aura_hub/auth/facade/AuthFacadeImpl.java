package com.course_aura_hub.auth.facade;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.ChangePasswordRequest;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.dto.ResetPasswordRequest;
import com.course_aura_hub.auth.dto.UserResponse;
import com.course_aura_hub.auth.dto.VerificationRequiredResponse;
import com.course_aura_hub.auth.email.AuthEmail;
import com.course_aura_hub.auth.email.AuthEmailService;
import com.course_aura_hub.auth.exception.AuthException;
import com.course_aura_hub.auth.model.SessionRevocationReason;
import com.course_aura_hub.auth.service.AuthService;
import com.course_aura_hub.auth.service.LoginOutcome;
import com.course_aura_hub.auth.service.MfaService;
import com.course_aura_hub.auth.service.PasswordResetService;
import com.course_aura_hub.auth.service.RefreshSessionService;
import com.course_aura_hub.auth.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFacadeImpl implements AuthFacade {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final RefreshSessionService refreshSessionService;
    private final MfaService mfaService;
    private final AuthEmailService authEmailService;
    private final AuthSecurityProperties properties;

    @Override
    public VerificationRequiredResponse register(RegisterRequest request) {
        log.info("Facade orchestrating registration");
        // Delivery failure surfaces as 503 so the user knows to retry; the account stays pending
        registrationService.register(request).ifPresent(authEmailService::send);
        return verificationRequired(request.email());
    }

    @Override
    public AuthResponse verifyEmail(String email, String code) {
        log.info("Facade orchestrating email verification");
        return registrationService.verifyEmail(email, code);
    }

    @Override
    public VerificationRequiredResponse resendVerificationCode(String email) {
        log.info("Facade orchestrating verification code resend");
        // Quiet: an error here would reveal that a pending account exists
        registrationService.resendVerificationCode(email).ifPresent(authEmailService::sendQuietly);
        return verificationRequired(email);
    }

    @Override
    public void requestPasswordReset(String email) {
        log.info("Facade orchestrating password reset request");
        passwordResetService.requestReset(email).ifPresent(authEmailService::sendQuietly);
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Facade orchestrating password reset");
        AuthEmail notification = passwordResetService.resetPassword(request.token(), request.password());
        authEmailService.sendQuietly(notification);
    }

    @Override
    public LoginOutcome login(LoginRequest request) {
        log.info("Facade orchestrating login");
        return authService.login(request);
    }

    @Override
    public AuthResponse completeMfaLogin(String challengeToken, String code, String recoveryCode) {
        log.info("Facade orchestrating two-factor login");
        return mfaService.completeLoginChallenge(challengeToken, code, recoveryCode);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Facade orchestrating token refresh");
        return refreshSessionService.rotate(refreshToken);
    }

    @Override
    public void logout(String refreshToken, UUID userId, UUID currentSessionId) {
        log.info("Facade orchestrating logout");
        if (StringUtils.hasText(refreshToken)) {
            refreshSessionService.revokeByRefreshToken(refreshToken);
        } else if (userId != null && currentSessionId != null) {
            refreshSessionService.revokeSession(userId, currentSessionId, SessionRevocationReason.LOGOUT);
        }
    }

    @Override
    public void logoutAll(UUID userId) {
        log.info("Facade orchestrating logout from all sessions");
        authService.logoutAll(userId);
    }

    @Override
    public UserResponse getCurrentUser(String email) {
        log.info("Facade orchestrating current user lookup");
        return authService.getCurrentUser(email);
    }

    @Override
    public AuthResponse changePassword(UUID userId, ChangePasswordRequest request) {
        log.info("Facade orchestrating password change");
        AuthResponse response = authService.changePassword(userId, request.currentPassword(), request.newPassword());
        authEmailService.sendQuietly(new AuthEmail.PasswordChanged(response.email(), response.firstName()));
        return response;
    }

    @Override
    public AuthResponse processOAuth2Callback(String provider, String code, String state) {
        throw new AuthException("OAuth2 callback should be handled by Spring Security at /login/oauth2/code/{provider}. " +
                "Use GET /oauth2/authorization/{provider} to initiate the OAuth2 flow.");
    }

    private VerificationRequiredResponse verificationRequired(String email) {
        AuthSecurityProperties.EmailVerification settings = properties.emailVerification();
        return new VerificationRequiredResponse(
                email.trim().toLowerCase(Locale.ROOT),
                true,
                settings.codeLength(),
                settings.codeTtl().toSeconds(),
                settings.resendCooldown().toSeconds()
        );
    }
}
