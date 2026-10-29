package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.email.AuthEmail;
import com.course_aura_hub.auth.exception.InvalidOneTimeCodeException;
import com.course_aura_hub.auth.mapper.UserMapper;
import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.TokenPurpose;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    static final String INVALID_CODE_MESSAGE = "Invalid or expired verification code";
    private static final int MAX_EXISTING_ACCOUNT_NOTICES_PER_HOUR = 3;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeTokenService oneTimeTokenService;
    private final RefreshSessionService refreshSessionService;
    private final AuthAuditService auditService;

    @Override
    @Transactional
    public Optional<AuthEmail> register(RegisterRequest request) {
        String email = normalize(request.email());
        // Hash before branching so new and existing emails take comparable time
        String encodedPassword = passwordEncoder.encode(request.password());

        Optional<User> existing = userRepository.findByEmailForUpdate(email);
        if (existing.isEmpty()) {
            User user = userMapper.toEntity(request);
            user.setEmail(email);
            user.setPassword(encodedPassword);
            user.setEmailVerified(false);
            user = userRepository.saveAndFlush(user);
            log.info("Created pending local account: {}", email);
            auditService.record(AuthAuditEntry.success(AuditEventType.REGISTRATION, user).reason("NEW_ACCOUNT").build());
            return Optional.of(issueVerificationCode(user));
        }

        User user = existing.get();
        if (user.isPendingEmailVerification()) {
            // Latest submission wins, so a squatter's earlier password never survives verification by the real owner
            user.setFirstName(request.firstName());
            user.setLastName(request.lastName());
            user.setPassword(encodedPassword);
            userRepository.save(user);
            auditService.record(AuthAuditEntry.success(AuditEventType.REGISTRATION, user).reason("PENDING_ACCOUNT_UPDATED").build());
            return issueVerificationCodeIfAllowed(user);
        }

        auditService.record(AuthAuditEntry.failure(AuditEventType.REGISTRATION_EXISTING_ACCOUNT, user, "EMAIL_ALREADY_REGISTERED").build());
        if (auditService.countRecent(user.getId(), AuditEventType.REGISTRATION_EXISTING_ACCOUNT, Duration.ofHours(1))
                >= MAX_EXISTING_ACCOUNT_NOTICES_PER_HOUR) {
            return Optional.empty();
        }
        return Optional.of(new AuthEmail.ExistingAccountRegistration(user.getEmail(), user.getFirstName()));
    }

    @Override
    @Transactional(noRollbackFor = InvalidOneTimeCodeException.class)
    public AuthResponse verifyEmail(String email, String code) {
        // The row lock serializes concurrent submissions of the same code: only one can consume it
        User user = userRepository.findByEmailForUpdate(normalize(email))
                .filter(candidate -> candidate.isPendingEmailVerification() && candidate.isEnabled())
                .orElseThrow(() -> new InvalidOneTimeCodeException(INVALID_CODE_MESSAGE));

        try {
            oneTimeTokenService.verifyCode(user, TokenPurpose.EMAIL_VERIFICATION, code, INVALID_CODE_MESSAGE);
        } catch (InvalidOneTimeCodeException ex) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.EMAIL_VERIFICATION, user, "INVALID_CODE").build());
            throw ex;
        }

        Instant now = Instant.now();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        userRepository.save(user);
        log.info("Email verified for account: {}", user.getEmail());
        auditService.record(AuthAuditEntry.success(AuditEventType.EMAIL_VERIFICATION, user).build());

        return refreshSessionService.startSession(user, false);
    }

    @Override
    @Transactional
    public Optional<AuthEmail> resendVerificationCode(String email) {
        return userRepository.findByEmailForUpdate(normalize(email))
                .filter(user -> user.isPendingEmailVerification() && user.isEnabled())
                .flatMap(this::issueVerificationCodeIfAllowed);
    }

    private Optional<AuthEmail> issueVerificationCodeIfAllowed(User user) {
        OneTimeTokenService.IssueDecision decision =
                oneTimeTokenService.checkIssuance(user, TokenPurpose.EMAIL_VERIFICATION, Instant.now());
        if (decision != OneTimeTokenService.IssueDecision.ALLOWED) {
            auditService.record(AuthAuditEntry.failure(AuditEventType.VERIFICATION_CODE_ISSUED, user, decision.name()).build());
            return Optional.empty();
        }
        return Optional.of(issueVerificationCode(user));
    }

    private AuthEmail issueVerificationCode(User user) {
        OneTimeTokenService.IssuedToken issued = oneTimeTokenService.issue(user, TokenPurpose.EMAIL_VERIFICATION, false);
        auditService.record(AuthAuditEntry.success(AuditEventType.VERIFICATION_CODE_ISSUED, user).build());
        return new AuthEmail.VerificationCode(
                user.getEmail(),
                user.getFirstName(),
                issued.rawValue(),
                oneTimeTokenService.policyFor(TokenPurpose.EMAIL_VERIFICATION).ttl()
        );
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
