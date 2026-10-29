package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.UserResponse;
import com.course_aura_hub.auth.exception.AccountDisabledException;
import com.course_aura_hub.auth.exception.AccountLockedException;
import com.course_aura_hub.auth.exception.AuthException;
import com.course_aura_hub.auth.exception.EmailNotVerifiedException;
import com.course_aura_hub.auth.exception.InvalidCredentialsException;
import com.course_aura_hub.auth.exception.InvalidTokenException;
import com.course_aura_hub.auth.mapper.UserMapper;
import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.AuditOutcome;
import com.course_aura_hub.auth.model.SessionRevocationReason;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final LoginAttemptService loginAttemptService;
    private final RefreshSessionService refreshSessionService;
    private final MfaService mfaService;
    private final AuthAuditService auditService;

    // Compared against when the account is unknown or has no password, so response time doesn't reveal which
    private volatile String timingEqualizationHash;

    @Override
    // Failed attempts and lockouts are written before the rejection is thrown; they must commit
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public LoginOutcome login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();

        Optional<User> found = userRepository.findByEmailForUpdate(normalizedEmail);
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), timingEqualizationHash());
            auditService.record(AuthAuditEntry.builder()
                    .type(AuditEventType.LOGIN)
                    .outcome(AuditOutcome.FAILURE)
                    .email(normalizedEmail)
                    .reason("UNKNOWN_ACCOUNT")
                    .build());
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (user.isLockedAt(now)) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.LOGIN, user, "ACCOUNT_LOCKED").build());
            throw new AccountLockedException();
        }

        if (!passwordMatches(request.password(), user)) {
            boolean lockedNow = loginAttemptService.recordFailure(user, now);
            auditService.record(AuthAuditEntry.failure(AuditEventType.LOGIN, user, "BAD_CREDENTIALS").build());
            log.warn("Invalid password attempt for email: {}", normalizedEmail);
            if (lockedNow) {
                throw new AccountLockedException();
            }
            throw new InvalidCredentialsException(INVALID_CREDENTIALS);
        }
        loginAttemptService.recordSuccess(user);

        // Status is revealed only to callers who proved the password
        if (!user.isEnabled()) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.LOGIN, user, "ACCOUNT_DISABLED").build());
            throw new AccountDisabledException();
        }
        if (user.isPendingEmailVerification()) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.LOGIN, user, "EMAIL_NOT_VERIFIED").build());
            throw new EmailNotVerifiedException();
        }

        if (user.isMfaEnabled()) {
            auditService.record(AuthAuditEntry.success(AuditEventType.LOGIN, user).reason("MFA_CHALLENGE_ISSUED").build());
            return new LoginOutcome.MfaRequired(mfaService.createLoginChallenge(user, request.rememberMe()));
        }

        AuthResponse auth = refreshSessionService.startSession(user, request.rememberMe());
        log.info("Successfully authenticated user: {}", normalizedEmail);
        auditService.record(AuthAuditEntry.success(AuditEventType.LOGIN, user)
                .sessionId(auth.sessionId())
                .metadata(Map.of("method", "PASSWORD"))
                .build());
        return new LoginOutcome.Authenticated(auth);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new InvalidTokenException("User not found"));
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        if (!StringUtils.hasText(user.getPassword())) {
            throw new AuthException("This account has no password yet. Use 'Forgot password' to set one.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.PASSWORD_CHANGED, user, "WRONG_CURRENT_PASSWORD").build());
            throw new AuthException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new AuthException("New password must be different from the current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        // Revoke every other device: refresh sessions and any access token still in flight
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshSessionService.revokeAllSessions(user.getId(), SessionRevocationReason.PASSWORD_CHANGED);
        log.info("Changed password for user: {}", user.getEmail());
        auditService.record(AuthAuditEntry.success(AuditEventType.PASSWORD_CHANGED, user).build());

        return refreshSessionService.startSession(user, false);
    }

    @Override
    @Transactional
    public void logoutAll(UUID userId) {
        userRepository.findByIdForUpdate(userId).ifPresent(user -> {
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
        });
        refreshSessionService.revokeAllSessions(userId, SessionRevocationReason.LOGOUT_ALL);
    }

    private boolean passwordMatches(String rawPassword, User user) {
        if (!StringUtils.hasText(user.getPassword())) {
            // OAuth-only account: do the same work, then fail with the generic message
            passwordEncoder.matches(rawPassword, timingEqualizationHash());
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    private String timingEqualizationHash() {
        String hash = timingEqualizationHash;
        if (hash == null) {
            hash = passwordEncoder.encode(UUID.randomUUID().toString());
            timingEqualizationHash = hash;
        }
        return hash;
    }
}
