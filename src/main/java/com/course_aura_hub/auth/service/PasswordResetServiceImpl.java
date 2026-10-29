package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.email.AuthEmail;
import com.course_aura_hub.auth.exception.InvalidOneTimeCodeException;
import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.AuditOutcome;
import com.course_aura_hub.auth.model.OneTimeToken;
import com.course_aura_hub.auth.model.SessionRevocationReason;
import com.course_aura_hub.auth.model.TokenPurpose;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    static final String INVALID_TOKEN_MESSAGE = "Invalid or expired password reset link";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokenService;
    private final RefreshSessionService refreshSessionService;
    private final AuthAuditService auditService;
    private final AuthSecurityProperties properties;

    @Override
    @Transactional
    public Optional<AuthEmail> requestReset(String email) {
        Optional<User> found = userRepository.findByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            auditService.record(AuthAuditEntry.builder()
                    .type(AuditEventType.PASSWORD_RESET_REQUESTED)
                    .outcome(AuditOutcome.FAILURE)
                    .email(email.trim().toLowerCase(Locale.ROOT))
                    .reason("UNKNOWN_ACCOUNT")
                    .build());
            return Optional.empty();
        }
        User user = found.get();
        if (!user.isEnabled() || user.isPendingEmailVerification()) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.PASSWORD_RESET_REQUESTED, user, "ACCOUNT_NOT_ELIGIBLE").build());
            return Optional.empty();
        }
        OneTimeTokenService.IssueDecision decision =
                oneTimeTokenService.checkIssuance(user, TokenPurpose.PASSWORD_RESET, Instant.now());
        if (decision != OneTimeTokenService.IssueDecision.ALLOWED) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.PASSWORD_RESET_REQUESTED, user, decision.name()).build());
            return Optional.empty();
        }

        OneTimeTokenService.IssuedToken issued = oneTimeTokenService.issue(user, TokenPurpose.PASSWORD_RESET, false);
        String link = UriComponentsBuilder.fromUriString(properties.passwordReset().resetUrl())
                .queryParam("token", issued.rawValue())
                .build()
                .toUriString();
        auditService.record(AuthAuditEntry.success(AuditEventType.PASSWORD_RESET_REQUESTED, user).build());
        return Optional.of(new AuthEmail.PasswordReset(user.getEmail(), user.getFirstName(), link, properties.passwordReset().tokenTtl()));
    }

    @Override
    @Transactional
    public AuthEmail resetPassword(String token, String newPassword) {
        // Lock order everywhere is user row first, then token rows
        UUID ownerId = oneTimeTokenService.findOwnerOfOpaqueToken(TokenPurpose.PASSWORD_RESET, token)
                .orElseThrow(() -> new InvalidOneTimeCodeException(INVALID_TOKEN_MESSAGE));
        User user = userRepository.findByIdForUpdate(ownerId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new InvalidOneTimeCodeException(INVALID_TOKEN_MESSAGE));
        OneTimeToken resetToken = oneTimeTokenService.lockUsableOpaqueToken(TokenPurpose.PASSWORD_RESET, token, INVALID_TOKEN_MESSAGE);
        oneTimeTokenService.consume(resetToken);

        Instant now = Instant.now();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (!user.isEmailVerified()) {
            // Following the emailed link proves ownership of the address
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(now);
        }
        // Revokes access tokens still within their lifetime on every device
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshSessionService.revokeAllSessions(user.getId(), SessionRevocationReason.PASSWORD_RESET);

        log.info("Password reset completed for account: {}", user.getEmail());
        auditService.record(AuthAuditEntry.success(AuditEventType.PASSWORD_RESET_COMPLETED, user).build());
        return new AuthEmail.PasswordChanged(user.getEmail(), user.getFirstName());
    }
}
