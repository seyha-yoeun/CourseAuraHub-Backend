package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.SessionResponse;
import com.course_aura_hub.auth.model.SessionRevocationReason;
import com.course_aura_hub.auth.model.User;

import java.util.List;
import java.util.UUID;

/**
 * Multi-device refresh sessions with rotation and reuse detection.
 */
public interface RefreshSessionService {

    /** Signs a user in on a new device: new session family, access token and refresh token. */
    AuthResponse startSession(User user, boolean rememberMe);

    /**
     * Exchanges a refresh token for a new pair. Presenting an already-rotated token outside the
     * grace window is treated as theft: the whole family is revoked.
     */
    AuthResponse rotate(String refreshToken);

    /** Logout of the session that owns this refresh token (expired tokens are accepted). */
    void revokeByRefreshToken(String refreshToken);

    void revokeSession(UUID userId, UUID sessionId, SessionRevocationReason reason);

    int revokeAllSessions(UUID userId, SessionRevocationReason reason);

    List<SessionResponse> listActiveSessions(UUID userId, UUID currentSessionId);

    boolean isSessionActive(UUID sessionId);

    int purgeExpired();
}
