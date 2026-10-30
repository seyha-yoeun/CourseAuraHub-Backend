package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.MfaChallengeResponse;
import com.course_aura_hub.auth.dto.MfaStatusResponse;
import com.course_aura_hub.auth.dto.RecoveryCodesResponse;
import com.course_aura_hub.auth.dto.TotpSetupResponse;
import com.course_aura_hub.auth.exception.AccountLockedException;
import com.course_aura_hub.auth.exception.AuthException;
import com.course_aura_hub.auth.exception.InvalidOneTimeCodeException;
import com.course_aura_hub.auth.exception.InvalidTokenException;
import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.MfaRecoveryCode;
import com.course_aura_hub.auth.model.OneTimeToken;
import com.course_aura_hub.auth.model.TokenPurpose;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.model.UserTotpCredential;
import com.course_aura_hub.auth.repository.MfaRecoveryCodeRepository;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.auth.repository.UserTotpCredentialRepository;
import com.course_aura_hub.auth.security.SecureTokenGenerator;
import com.course_aura_hub.auth.security.TokenHasher;
import com.course_aura_hub.auth.security.mfa.Base32;
import com.course_aura_hub.auth.security.mfa.MfaSecretCipher;
import com.course_aura_hub.auth.security.mfa.TotpGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    static final String INVALID_CHALLENGE_MESSAGE = "Invalid or expired two-factor challenge";
    static final String INVALID_CODE_MESSAGE = "Invalid two-factor code";
    private static final int SECRET_BYTES = 20;
    private static final String RECOVERY_HASH_PURPOSE = "MFA_RECOVERY_CODE";

    private final UserRepository userRepository;
    private final UserTotpCredentialRepository credentialRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final OneTimeTokenService oneTimeTokenService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshSessionService refreshSessionService;
    private final AuthAuditService auditService;
    private final TotpGenerator totpGenerator;
    private final MfaSecretCipher secretCipher;
    private final SecureTokenGenerator generator;
    private final TokenHasher tokenHasher;
    private final PasswordEncoder passwordEncoder;
    private final AuthSecurityProperties properties;

    @Override
    @Transactional(readOnly = true)
    public MfaStatusResponse status(UUID userId) {
        User user = loadUser(userId);
        Optional<UserTotpCredential> credential = credentialRepository.findById(userId);
        return new MfaStatusResponse(
                user.isMfaEnabled(),
                credential.map(c -> !c.isConfirmed()).orElse(false),
                user.isMfaEnabled() ? recoveryCodeRepository.countByUserIdAndUsedAtIsNull(userId) : 0,
                secretCipher.isAvailable()
        );
    }

    @Override
    @Transactional
    public TotpSetupResponse beginTotpSetup(UUID userId) {
        User user = lockUser(userId);
        if (user.isMfaEnabled()) {
            throw new AuthException("Two-factor authentication is already enabled");
        }
        byte[] secret = generator.randomBytes(SECRET_BYTES);
        Instant now = Instant.now();
        UserTotpCredential credential = credentialRepository.findById(userId)
                .orElseGet(() -> UserTotpCredential.builder().userId(userId).createdAt(now).build());
        credential.setSecretCiphertext(secretCipher.encrypt(secret));
        credential.setConfirmedAt(null);
        credential.setLastUsedStep(null);
        credentialRepository.save(credential);
        auditService.record(AuthAuditEntry.success(AuditEventType.MFA_ENROLLMENT_STARTED, user).build());

        String base32Secret = Base32.encode(secret);
        return new TotpSetupResponse(base32Secret, otpauthUri(user.getEmail(), base32Secret),
                TotpGenerator.DIGITS, TotpGenerator.PERIOD_SECONDS);
    }

    @Override
    @Transactional
    public RecoveryCodesResponse confirmTotpSetup(UUID userId, String code) {
        User user = lockUser(userId);
        if (user.isMfaEnabled()) {
            throw new AuthException("Two-factor authentication is already enabled");
        }
        UserTotpCredential credential = credentialRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Start two-factor setup first"));
        if (!acceptTotp(credential, code)) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.MFA_ENABLED, user, "INVALID_CODE").build());
            throw new InvalidOneTimeCodeException(INVALID_CODE_MESSAGE);
        }
        credential.setConfirmedAt(Instant.now());
        credentialRepository.save(credential);
        user.setMfaEnabled(true);
        userRepository.save(user);
        auditService.record(AuthAuditEntry.success(AuditEventType.MFA_ENABLED, user).build());
        return new RecoveryCodesResponse(replaceRecoveryCodes(userId));
    }

    @Override
    @Transactional
    public void disable(UUID userId, String code, String password) {
        User user = lockUser(userId);
        if (!user.isMfaEnabled()) {
            throw new AuthException("Two-factor authentication is not enabled");
        }
        if (StringUtils.hasText(user.getPassword())
                && (password == null || !passwordEncoder.matches(password, user.getPassword()))) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.MFA_DISABLED, user, "WRONG_PASSWORD").build());
            throw new AuthException("Current password is incorrect");
        }
        if (!verifySecondFactor(user, code)) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.MFA_DISABLED, user, "INVALID_CODE").build());
            throw new InvalidOneTimeCodeException(INVALID_CODE_MESSAGE);
        }
        credentialRepository.deleteById(userId);
        recoveryCodeRepository.deleteAllForUser(userId);
        oneTimeTokenService.revokeActive(user, TokenPurpose.MFA_LOGIN_CHALLENGE);
        user.setMfaEnabled(false);
        userRepository.save(user);
        auditService.record(AuthAuditEntry.success(AuditEventType.MFA_DISABLED, user).build());
    }

    @Override
    @Transactional
    public RecoveryCodesResponse regenerateRecoveryCodes(UUID userId, String code) {
        User user = lockUser(userId);
        if (!user.isMfaEnabled()) {
            throw new AuthException("Two-factor authentication is not enabled");
        }
        UserTotpCredential credential = credentialRepository.findById(userId)
                .orElseThrow(MfaServiceImpl::missingCredential);
        if (!acceptTotp(credential, code)) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.MFA_RECOVERY_CODES_REGENERATED, user, "INVALID_CODE").build());
            throw new InvalidOneTimeCodeException(INVALID_CODE_MESSAGE);
        }
        auditService.record(AuthAuditEntry.success(AuditEventType.MFA_RECOVERY_CODES_REGENERATED, user).build());
        return new RecoveryCodesResponse(replaceRecoveryCodes(userId));
    }

    @Override
    public MfaChallengeResponse createLoginChallenge(User user, boolean rememberMe) {
        OneTimeTokenService.IssuedToken issued = oneTimeTokenService.issue(user, TokenPurpose.MFA_LOGIN_CHALLENGE, rememberMe);
        return new MfaChallengeResponse(
                true,
                issued.rawValue(),
                List.of("TOTP", "RECOVERY_CODE"),
                properties.mfa().challengeTtl().toSeconds()
        );
    }

    @Override
    // Wrong codes count against the challenge and the account lockout; those writes must commit
    @Transactional(noRollbackFor = {InvalidOneTimeCodeException.class, AccountLockedException.class})
    public AuthResponse completeLoginChallenge(String challengeToken, String code, String recoveryCode) {
        UUID ownerId = oneTimeTokenService.findOwnerOfOpaqueToken(TokenPurpose.MFA_LOGIN_CHALLENGE, challengeToken)
                .orElseThrow(() -> new InvalidOneTimeCodeException(INVALID_CHALLENGE_MESSAGE));
        User user = lockUser(ownerId);
        OneTimeToken challenge = oneTimeTokenService.lockUsableOpaqueToken(
                TokenPurpose.MFA_LOGIN_CHALLENGE, challengeToken, INVALID_CHALLENGE_MESSAGE);

        Instant now = Instant.now();
        if (user.isLockedAt(now)) {
            throw new AccountLockedException();
        }
        if (!user.isEnabled() || user.isPendingEmailVerification() || !user.isMfaEnabled()) {
            throw new InvalidOneTimeCodeException(INVALID_CHALLENGE_MESSAGE);
        }

        boolean usedRecoveryCode = !StringUtils.hasText(code) && StringUtils.hasText(recoveryCode);
        boolean accepted = usedRecoveryCode ? useRecoveryCode(user, recoveryCode) : verifyTotp(user, code);
        if (!accepted) {
            oneTimeTokenService.recordFailedAttempt(challenge, now);
            boolean lockedNow = loginAttemptService.recordFailure(user, now);
            auditService.record(AuthAuditEntry.failure(AuditEventType.MFA_CHALLENGE, user, "INVALID_CODE").build());
            if (lockedNow) {
                oneTimeTokenService.revokeActive(user, TokenPurpose.MFA_LOGIN_CHALLENGE);
                throw new AccountLockedException();
            }
            throw new InvalidOneTimeCodeException(INVALID_CODE_MESSAGE);
        }

        oneTimeTokenService.consume(challenge);
        loginAttemptService.recordSuccess(user);
        AuthResponse auth = refreshSessionService.startSession(user, challenge.isRememberMe());
        auditService.record(AuthAuditEntry.success(AuditEventType.MFA_CHALLENGE, user)
                .sessionId(auth.sessionId())
                .metadata(Map.of("method", usedRecoveryCode ? "RECOVERY_CODE" : "TOTP"))
                .build());
        return auth;
    }

    private boolean verifySecondFactor(User user, String code) {
        String trimmed = code == null ? "" : code.trim();
        return trimmed.matches("\\d{" + TotpGenerator.DIGITS + "}") ? verifyTotp(user, trimmed) : useRecoveryCode(user, trimmed);
    }

    private boolean verifyTotp(User user, String code) {
        UserTotpCredential credential = credentialRepository.findById(user.getId())
                .filter(UserTotpCredential::isConfirmed)
                .orElseThrow(MfaServiceImpl::missingCredential);
        return acceptTotp(credential, code);
    }

    /** Verifies and records the step so the same code cannot be replayed. */
    private boolean acceptTotp(UserTotpCredential credential, String code) {
        byte[] secret = secretCipher.decrypt(credential.getSecretCiphertext());
        OptionalLong step = totpGenerator.verify(secret, code == null ? null : code.trim(),
                credential.getLastUsedStep(), Instant.now());
        if (step.isEmpty()) {
            return false;
        }
        credential.setLastUsedStep(step.getAsLong());
        credentialRepository.save(credential);
        return true;
    }

    private boolean useRecoveryCode(User user, String rawCode) {
        String normalized = normalizeRecoveryCode(rawCode);
        if (normalized.isEmpty()) {
            return false;
        }
        Optional<MfaRecoveryCode> match = recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(
                user.getId(), tokenHasher.hash(RECOVERY_HASH_PURPOSE, user.getId().toString(), normalized));
        match.ifPresent(recovery -> {
            recovery.setUsedAt(Instant.now());
            recoveryCodeRepository.save(recovery);
        });
        return match.isPresent();
    }

    private List<String> replaceRecoveryCodes(UUID userId) {
        recoveryCodeRepository.deleteAllForUser(userId);
        Instant now = Instant.now();
        List<String> codes = new ArrayList<>();
        List<MfaRecoveryCode> entities = new ArrayList<>();
        for (int i = 0; i < properties.mfa().recoveryCodeCount(); i++) {
            String code = generator.recoveryCode();
            codes.add(code);
            entities.add(MfaRecoveryCode.builder()
                    .userId(userId)
                    .codeHash(tokenHasher.hash(RECOVERY_HASH_PURPOSE, userId.toString(), normalizeRecoveryCode(code)))
                    .createdAt(now)
                    .build());
        }
        recoveryCodeRepository.saveAll(entities);
        return codes;
    }

    private String otpauthUri(String accountEmail, String base32Secret) {
        String issuer = properties.mfa().issuer();
        String label = encode(issuer) + ":" + encode(accountEmail);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + TotpGenerator.DIGITS
                + "&period=" + TotpGenerator.PERIOD_SECONDS;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new InvalidTokenException("User not found"));
    }

    private User lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(() -> new InvalidTokenException("User not found"));
    }

    private static AuthException missingCredential() {
        log.error("User has two-factor enabled but no confirmed TOTP credential");
        return new AuthException("Two-factor authentication is misconfigured for this account. Contact support.");
    }
}
