package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.exception.OAuth2LoginException;
import com.course_aura_hub.auth.exception.OAuth2LoginException.Reason;
import com.course_aura_hub.auth.mapper.UserMapper;
import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.AuditOutcome;
import com.course_aura_hub.auth.model.OAuthIdentity;
import com.course_aura_hub.auth.model.TokenPurpose;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.OAuthIdentityRepository;
import com.course_aura_hub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Signs users in with an OAuth2 provider identity.
 *
 * <p>Identities are matched by provider + provider subject. An identity is linked to an existing
 * account only when the provider vouches for the email address; a bare string match is never enough.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final UserRepository userRepository;
    private final OAuthIdentityRepository identityRepository;
    private final UserMapper userMapper;
    private final RefreshSessionService refreshSessionService;
    private final OneTimeTokenService oneTimeTokenService;
    private final MfaService mfaService;
    private final AuthAuditService auditService;

    /**
     * @throws OAuth2LoginException with a client-safe reason when the login cannot complete
     */
    @Transactional
    public LoginOutcome processOAuth2Login(OAuth2UserDto profile) {
        if (!StringUtils.hasText(profile.providerId())) {
            throw reject(profile, null, Reason.INVALID_PROFILE);
        }
        Instant now = Instant.now();

        User user = identityRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerId())
                .map(identity -> signInWithLinkedIdentity(identity, profile, now))
                .orElseGet(() -> signInWithNewIdentity(profile, now));

        if (!user.isEnabled()) {
            throw reject(profile, user, Reason.ACCOUNT_UNAVAILABLE);
        }
        if (user.isLockedAt(now)) {
            throw reject(profile, user, Reason.ACCOUNT_LOCKED);
        }
        if (!StringUtils.hasText(user.getImageUrl()) && StringUtils.hasText(profile.imageUrl())) {
            user.setImageUrl(profile.imageUrl());
            userRepository.save(user);
        }

        if (user.isMfaEnabled()) {
            auditService.record(AuthAuditEntry.success(AuditEventType.OAUTH2_LOGIN, user)
                    .reason("MFA_CHALLENGE_ISSUED").metadata(providerMetadata(profile)).build());
            return new LoginOutcome.MfaRequired(mfaService.createLoginChallenge(user, false));
        }

        AuthResponse auth = refreshSessionService.startSession(user, false);
        auditService.record(AuthAuditEntry.success(AuditEventType.OAUTH2_LOGIN, user)
                .sessionId(auth.sessionId()).metadata(providerMetadata(profile)).build());
        return new LoginOutcome.Authenticated(auth);
    }

    private User signInWithLinkedIdentity(OAuthIdentity identity, OAuth2UserDto profile, Instant now) {
        User user = userRepository.findByIdForUpdate(identity.getUser().getId())
                .orElseThrow(() -> reject(profile, null, Reason.ACCOUNT_UNAVAILABLE));
        identity.setLastLoginAt(now);
        identity.setEmail(normalize(profile.email()));
        identity.setEmailVerified(profile.emailVerified());
        identityRepository.save(identity);
        return user;
    }

    private User signInWithNewIdentity(OAuth2UserDto profile, Instant now) {
        String email = normalize(profile.email());
        if (!StringUtils.hasText(email)) {
            throw reject(profile, null, Reason.EMAIL_REQUIRED);
        }

        Optional<User> existing = userRepository.findByEmailForUpdate(email);
        if (existing.isEmpty()) {
            User user = userMapper.toEntity(profile);
            user.setEmail(email);
            user.setEmailVerified(profile.emailVerified());
            user.setEmailVerifiedAt(profile.emailVerified() ? now : null);
            user = userRepository.saveAndFlush(user);
            link(user, profile, now);
            log.info("Created account from {} identity: {}", profile.provider(), email);
            auditService.record(AuthAuditEntry.success(AuditEventType.OAUTH2_ACCOUNT_CREATED, user)
                    .metadata(providerMetadata(profile)).build());
            return user;
        }

        User user = existing.get();
        if (!profile.emailVerified()) {
            // The provider didn't prove the caller owns this mailbox; linking would allow account takeover
            throw reject(profile, user, Reason.ACCOUNT_LINK_REQUIRED);
        }
        if (identityRepository.existsByUserIdAndProvider(user.getId(), profile.provider())) {
            throw reject(profile, user, Reason.ACCOUNT_LINK_CONFLICT);
        }
        if (user.isPendingEmailVerification()) {
            // Unverified local sign-up for an address the provider just proved: whoever created it
            // never controlled the mailbox, so its password must not survive
            user.setPassword(null);
            oneTimeTokenService.revokeActive(user, TokenPurpose.EMAIL_VERIFICATION);
        } else if (!user.isEmailVerified()) {
            throw reject(profile, user, Reason.ACCOUNT_LINK_REQUIRED);
        }
        user.setEmailVerified(true);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }
        userRepository.save(user);
        link(user, profile, now);
        auditService.record(AuthAuditEntry.success(AuditEventType.OAUTH2_ACCOUNT_LINKED, user)
                .metadata(providerMetadata(profile)).build());
        return user;
    }

    private void link(User user, OAuth2UserDto profile, Instant now) {
        identityRepository.save(OAuthIdentity.builder()
                .user(user)
                .provider(profile.provider())
                .providerUserId(profile.providerId())
                .email(normalize(profile.email()))
                .emailVerified(profile.emailVerified())
                .createdAt(now)
                .lastLoginAt(now)
                .build());
    }

    private OAuth2LoginException reject(OAuth2UserDto profile, User user, Reason reason) {
        AuditEventType type = reason == Reason.ACCOUNT_LINK_REQUIRED || reason == Reason.ACCOUNT_LINK_CONFLICT
                ? AuditEventType.OAUTH2_ACCOUNT_LINKED
                : AuditEventType.OAUTH2_LOGIN;
        AuthAuditEntry.AuthAuditEntryBuilder entry = user != null
                ? AuthAuditEntry.failure(type, user, reason.name())
                : AuthAuditEntry.builder().type(type).outcome(AuditOutcome.FAILURE).email(normalize(profile.email())).reason(reason.name());
        auditService.record(entry.metadata(providerMetadata(profile)).build());
        log.warn("OAuth2 login via {} rejected: {}", profile.provider(), reason);
        return new OAuth2LoginException(reason);
    }

    private static Map<String, String> providerMetadata(OAuth2UserDto profile) {
        return Map.of(
                "provider", String.valueOf(profile.provider()),
                "providerEmailVerified", String.valueOf(profile.emailVerified())
        );
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
