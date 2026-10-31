package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.exception.InvalidOneTimeCodeException;
import com.course_aura_hub.auth.model.OneTimeToken;
import com.course_aura_hub.auth.model.TokenPurpose;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.OneTimeTokenRepository;
import com.course_aura_hub.auth.security.SecureTokenGenerator;
import com.course_aura_hub.auth.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and redeems hashed one-time secrets for every {@link TokenPurpose}.
 *
 * <p>Methods are not transactional on purpose: callers own the transaction and must hold the
 * owning user's row lock (see {@code UserRepository#findByEmailForUpdate}). Failed attempts are
 * written before an {@link InvalidOneTimeCodeException} is thrown, so callers declare
 * {@code noRollbackFor = InvalidOneTimeCodeException.class} to keep the attempt counter.</p>
 */
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    private static final int OPAQUE_TOKEN_BYTES = 32;
    private static final Duration TELEGRAM_LINK_TTL = Duration.ofMinutes(10);

    private final OneTimeTokenRepository repository;
    private final TokenHasher tokenHasher;
    private final SecureTokenGenerator generator;
    private final AuthSecurityProperties properties;

    public record IssuedToken(OneTimeToken token, String rawValue) {
    }

    public enum IssueDecision {
        ALLOWED,
        COOLDOWN,
        HOURLY_LIMIT
    }

    /** Per-purpose rules, resolved from configuration. */
    public record Policy(int codeLength, Duration ttl, int maxAttempts, Duration cooldown, int maxPerHour) {
    }

    public Policy policyFor(TokenPurpose purpose) {
        return switch (purpose) {
            case TELEGRAM_LINK -> new Policy(0, TELEGRAM_LINK_TTL, 1, Duration.ZERO, Integer.MAX_VALUE);
            case EMAIL_VERIFICATION -> {
                AuthSecurityProperties.EmailVerification p = properties.emailVerification();
                yield new Policy(p.codeLength(), p.codeTtl(), p.maxAttempts(), p.resendCooldown(), p.maxCodesPerHour());
            }
            case PASSWORD_RESET -> {
                AuthSecurityProperties.PasswordReset p = properties.passwordReset();
                yield new Policy(0, p.tokenTtl(), 1, p.requestCooldown(), p.maxRequestsPerHour());
            }
            case MFA_LOGIN_CHALLENGE -> {
                AuthSecurityProperties.Mfa p = properties.mfa();
                yield new Policy(0, p.challengeTtl(), p.maxChallengeAttempts(), Duration.ZERO, Integer.MAX_VALUE);
            }
        };
    }

    /** Throttling for user-triggered sends (cooldown between sends and an hourly cap). */
    public IssueDecision checkIssuance(User user, TokenPurpose purpose, Instant now) {
        Policy policy = policyFor(purpose);
        if (!policy.cooldown().isZero()) {
            Optional<OneTimeToken> latest = repository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose);
            if (latest.isPresent() && latest.get().getCreatedAt().plus(policy.cooldown()).isAfter(now)) {
                return IssueDecision.COOLDOWN;
            }
        }
        if (policy.maxPerHour() != Integer.MAX_VALUE) {
            long issuedLastHour = repository.countByUserIdAndPurposeAndCreatedAtAfter(
                    user.getId(), purpose, now.minus(Duration.ofHours(1)));
            if (issuedLastHour >= policy.maxPerHour()) {
                return IssueDecision.HOURLY_LIMIT;
            }
        }
        return IssueDecision.ALLOWED;
    }

    /** Revokes any usable secret for this user and purpose, then issues a new one. */
    public IssuedToken issue(User user, TokenPurpose purpose, boolean rememberMe) {
        Instant now = Instant.now();
        revokeActive(user, purpose, now);

        Policy policy = policyFor(purpose);
        String raw = purpose.isNumeric()
                ? generator.numericCode(policy.codeLength())
                : generator.urlSafeToken(OPAQUE_TOKEN_BYTES);

        OneTimeToken token = OneTimeToken.builder()
                .user(user)
                .purpose(purpose)
                .tokenHash(hash(purpose, user.getId(), raw))
                .maxAttempts(policy.maxAttempts())
                .rememberMe(rememberMe)
                .createdAt(now)
                .expiresAt(now.plus(policy.ttl()))
                .build();
        return new IssuedToken(repository.save(token), raw);
    }

    /**
     * Redeems a numeric code typed by the user. Wrong codes count towards the attempt limit;
     * reaching it revokes the code.
     */
    public void verifyCode(User user, TokenPurpose purpose, String rawCode, String failureMessage) {
        Instant now = Instant.now();
        OneTimeToken token = repository.findActiveForUpdate(user.getId(), purpose)
                .orElseThrow(() -> new InvalidOneTimeCodeException(failureMessage));
        if (!token.isUsableAt(now)) {
            throw new InvalidOneTimeCodeException(failureMessage);
        }
        if (rawCode == null || !tokenHasher.matches(token.getTokenHash(), purpose.name(), user.getId().toString(), rawCode)) {
            recordFailedAttempt(token, now);
            throw new InvalidOneTimeCodeException(failureMessage);
        }
        token.setConsumedAt(now);
        repository.save(token);
    }

    /** Owner of an opaque token, read without locking so callers can lock the user row first. */
    public Optional<UUID> findOwnerOfOpaqueToken(TokenPurpose purpose, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findOwnerId(purpose, hash(purpose, null, rawToken));
    }

    /** Locks and returns a usable opaque token, or throws. The caller decides when to consume it. */
    public OneTimeToken lockUsableOpaqueToken(TokenPurpose purpose, String rawToken, String failureMessage) {
        OneTimeToken token = repository.findByPurposeAndTokenHashForUpdate(purpose, hash(purpose, null, rawToken))
                .orElseThrow(() -> new InvalidOneTimeCodeException(failureMessage));
        if (!token.isUsableAt(Instant.now())) {
            throw new InvalidOneTimeCodeException(failureMessage);
        }
        return token;
    }

    public void consume(OneTimeToken token) {
        token.setConsumedAt(Instant.now());
        repository.save(token);
    }

    public void recordFailedAttempt(OneTimeToken token, Instant now) {
        token.setAttempts(token.getAttempts() + 1);
        if (token.getAttempts() >= token.getMaxAttempts()) {
            token.setRevokedAt(now);
        }
        repository.save(token);
    }

    public void revokeActive(User user, TokenPurpose purpose) {
        revokeActive(user, purpose, Instant.now());
    }

    private void revokeActive(User user, TokenPurpose purpose, Instant now) {
        repository.findActiveForUpdate(user.getId(), purpose).ifPresent(active -> {
            active.setRevokedAt(now);
            // Flush now: the partial unique index allows only one active row, and Hibernate
            // would otherwise run the INSERT of the replacement before this UPDATE
            repository.saveAndFlush(active);
        });
    }

    private String hash(TokenPurpose purpose, UUID userId, String raw) {
        // Numeric codes are bound to the user (short and not globally unique); opaque tokens are looked up by hash alone
        return purpose.isNumeric()
                ? tokenHasher.hash(purpose.name(), userId.toString(), raw)
                : tokenHasher.hash(purpose.name(), raw);
    }
}
