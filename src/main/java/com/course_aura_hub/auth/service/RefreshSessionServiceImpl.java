package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.SessionResponse;
import com.course_aura_hub.auth.exception.InvalidTokenException;
import com.course_aura_hub.auth.exception.RefreshTokenReuseException;
import com.course_aura_hub.auth.exception.TokenExpiredException;
import com.course_aura_hub.auth.mapper.UserMapper;
import com.course_aura_hub.auth.model.AuditEventType;
import com.course_aura_hub.auth.model.RefreshSession;
import com.course_aura_hub.auth.model.SessionRevocationReason;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.RefreshSessionRepository;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.auth.security.ClientInfo;
import com.course_aura_hub.auth.security.ClientInfoResolver;
import com.course_aura_hub.auth.security.JwtTokenProvider;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshSessionServiceImpl implements RefreshSessionService {

    private final RefreshSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final ClientInfoResolver clientInfoResolver;
    private final AuthAuditService auditService;
    private final AuthSecurityProperties properties;

    @Override
    @Transactional
    public AuthResponse startSession(User user, boolean rememberMe) {
        Instant now = Instant.now();
        enforceSessionLimit(user, now);
        UUID familyId = UUID.randomUUID();
        IssuedSession issued = issue(user, familyId, rememberMe, now, now);
        auditService.record(AuthAuditEntry.success(AuditEventType.SESSION_CREATED, user)
                .sessionId(familyId)
                .metadata(Map.of("rememberMe", String.valueOf(rememberMe)))
                .build());
        return issued.response();
    }

    @Override
    // Reuse detection revokes the family and then rejects the request; that revocation must commit
    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public AuthResponse rotate(String refreshToken) {
        Claims claims = parseRefreshClaims(refreshToken);
        Instant now = Instant.now();

        Optional<RefreshSession> found = sessionRepository.findByTokenHashForUpdate(jwtTokenProvider.hashRefreshToken(refreshToken));
        if (found.isEmpty()) {
            return rotateLegacyToken(refreshToken, claims);
        }

        RefreshSession current = found.get();
        User user = current.getUser();
        if (current.getRevokedAt() != null) {
            rejectRevokedToken(current, user, now);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidTokenException("Refresh token has expired");
        }
        assertUserMaySignIn(user, now);

        current.setRevokedAt(now);
        current.setRevokedReason(SessionRevocationReason.ROTATED);
        current.setLastUsedAt(now);
        // Flush the revocation before inserting the child: only one active row per family is allowed
        sessionRepository.saveAndFlush(current);

        IssuedSession child = issue(user, current.getFamilyId(), current.isRememberMe(), current.getSessionStartedAt(), now);
        current.setReplacedBy(child.session().getId());
        sessionRepository.save(current);
        return child.response();
    }

    @Override
    @Transactional
    public void revokeByRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        Optional<RefreshSession> session = sessionRepository.findByTokenHashForUpdate(jwtTokenProvider.hashRefreshToken(refreshToken));
        if (session.isPresent()) {
            revokeFamily(session.get().getUser(), session.get().getFamilyId(), SessionRevocationReason.LOGOUT);
            return;
        }
        revokeLegacyToken(refreshToken);
    }

    @Override
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, SessionRevocationReason reason) {
        if (sessionId == null || !sessionRepository.existsByFamilyIdAndUserId(sessionId, userId)) {
            throw new ResourceNotFoundException("Session not found");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        revokeFamily(user, sessionId, reason);
    }

    @Override
    @Transactional
    public int revokeAllSessions(UUID userId, SessionRevocationReason reason) {
        int revoked = sessionRepository.revokeAllForUser(userId, reason, Instant.now());
        userRepository.findById(userId).ifPresent(user -> {
            clearLegacyRefreshToken(user);
            auditService.record(AuthAuditEntry.success(AuditEventType.SESSION_REVOKED, user)
                    .reason(reason.name())
                    .metadata(Map.of("scope", "ALL", "revokedTokens", String.valueOf(revoked)))
                    .build());
        });
        return revoked;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> listActiveSessions(UUID userId, UUID currentSessionId) {
        return sessionRepository.findActiveByUserId(userId, Instant.now()).stream()
                .map(session -> new SessionResponse(
                        session.getFamilyId(),
                        session.getSessionStartedAt(),
                        session.getCreatedAt(),
                        session.getExpiresAt(),
                        session.getIpAddress(),
                        session.getUserAgent(),
                        session.isRememberMe(),
                        session.getFamilyId().equals(currentSessionId)
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        return sessionRepository.isFamilyActive(sessionId, Instant.now());
    }

    @Override
    @Transactional
    public int purgeExpired() {
        // Rotated rows are kept until the family would have expired anyway, so reuse stays detectable
        return sessionRepository.deleteExpiredBefore(Instant.now().minus(properties.tokenRetention()));
    }

    private record IssuedSession(RefreshSession session, AuthResponse response) {
    }

    private IssuedSession issue(User user, UUID familyId, boolean rememberMe, Instant sessionStartedAt, Instant now) {
        long refreshTtlMillis = rememberMe
                ? properties.session().rememberMeTtl().toMillis()
                : jwtTokenProvider.getRefreshTokenExpiration();
        String accessToken = jwtTokenProvider.generateAccessToken(user, familyId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, familyId, refreshTtlMillis);
        ClientInfo client = clientInfoResolver.current();

        RefreshSession session = sessionRepository.save(RefreshSession.builder()
                .user(user)
                .familyId(familyId)
                .tokenHash(jwtTokenProvider.hashRefreshToken(refreshToken))
                .rememberMe(rememberMe)
                .sessionStartedAt(sessionStartedAt)
                .createdAt(now)
                .expiresAt(now.plusMillis(refreshTtlMillis))
                .ipAddress(client.ipAddress())
                .userAgent(client.userAgent())
                .build());

        AuthResponse response = userMapper.toAuthResponse(
                user, accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpiration(), familyId, refreshTtlMillis);
        return new IssuedSession(session, response);
    }

    private void rejectRevokedToken(RefreshSession presented, User user, Instant now) {
        boolean rotated = presented.getRevokedReason() == SessionRevocationReason.ROTATED;
        boolean withinGrace = presented.getRevokedAt().plus(properties.session().reuseGracePeriod()).isAfter(now);
        if (rotated && withinGrace) {
            // Two near-simultaneous refreshes from the same client: reject the loser without punishing the session
            log.info("Refresh token presented again within grace period for user: {}", user.getEmail());
            throw new InvalidTokenException("Refresh token has already been used");
        }
        if (rotated) {
            int revoked = sessionRepository.revokeFamily(presented.getFamilyId(), SessionRevocationReason.REUSE_DETECTED, now);
            log.warn("Refresh token reuse detected; revoked session family for user: {}", user.getEmail());
            auditService.record(AuthAuditEntry.failure(AuditEventType.REFRESH_TOKEN_REUSE_DETECTED, user, "ROTATED_TOKEN_REPLAYED")
                    .sessionId(presented.getFamilyId())
                    .metadata(Map.of("revokedTokens", String.valueOf(revoked)))
                    .build());
            throw new RefreshTokenReuseException();
        }
        throw new InvalidTokenException("Invalid refresh token");
    }

    private void revokeFamily(User user, UUID familyId, SessionRevocationReason reason) {
        sessionRepository.revokeFamily(familyId, reason, Instant.now());
        auditService.record(AuthAuditEntry.success(AuditEventType.SESSION_REVOKED, user)
                .sessionId(familyId)
                .reason(reason.name())
                .build());
    }

    private void enforceSessionLimit(User user, Instant now) {
        List<RefreshSession> active = sessionRepository.findActiveByUserId(user.getId(), now);
        int maxActive = properties.session().maxActivePerUser();
        // Newest first; make room for the session about to be created by revoking the oldest
        for (int i = maxActive - 1; i < active.size(); i++) {
            revokeFamily(user, active.get(i).getFamilyId(), SessionRevocationReason.SESSION_LIMIT);
        }
    }

    private void assertUserMaySignIn(User user, Instant now) {
        if (!user.isEnabled() || user.isPendingEmailVerification()) {
            throw new InvalidTokenException("Session is no longer valid");
        }
        if (user.isLockedAt(now)) {
            throw new InvalidTokenException("User account is temporarily locked");
        }
    }

    private Claims parseRefreshClaims(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new InvalidTokenException("Refresh token is required");
        }
        Claims claims;
        try {
            claims = jwtTokenProvider.extractAllClaims(refreshToken);
        } catch (TokenExpiredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        if (!JwtTokenProvider.REFRESH_TOKEN_TYPE.equalsIgnoreCase(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
            throw new InvalidTokenException("Provided token is not a valid refresh token");
        }
        if (!StringUtils.hasText(claims.getSubject())) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        return claims;
    }

    /**
     * Refresh tokens issued before multi-device sessions were stored as a single hash on users.
     * Accept such a token once and move it into a proper session family.
     */
    private AuthResponse rotateLegacyToken(String refreshToken, Claims claims) {
        if (JwtTokenProvider.sessionIdOf(claims) != null) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        User user = userRepository.findByEmailForUpdate(claims.getSubject())
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));
        if (!jwtTokenProvider.matchesRefreshToken(refreshToken, user.getRefreshToken())) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        assertUserMaySignIn(user, Instant.now());
        clearLegacyRefreshToken(user);
        long lifetimeMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        boolean rememberMe = lifetimeMillis > jwtTokenProvider.getRefreshTokenExpiration();
        log.info("Migrated legacy refresh token to a session family for user: {}", user.getEmail());
        return startSession(user, rememberMe);
    }

    private void revokeLegacyToken(String refreshToken) {
        try {
            Claims claims = jwtTokenProvider.extractAllClaims(refreshToken);
            userRepository.findByEmail(claims.getSubject())
                    .filter(user -> jwtTokenProvider.matchesRefreshToken(refreshToken, user.getRefreshToken()))
                    .ifPresent(this::clearLegacyRefreshToken);
        } catch (RuntimeException ex) {
            log.debug("Logout with unrecognised refresh token");
        }
    }

    private void clearLegacyRefreshToken(User user) {
        if (user.getRefreshToken() != null) {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiresAt(null);
            user.setTokensIssuedAt(null);
            user.setAccessToken(null);
            userRepository.save(user);
        }
    }
}
