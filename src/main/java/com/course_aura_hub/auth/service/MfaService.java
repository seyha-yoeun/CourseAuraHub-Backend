package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.MfaChallengeResponse;
import com.course_aura_hub.auth.dto.MfaStatusResponse;
import com.course_aura_hub.auth.dto.RecoveryCodesResponse;
import com.course_aura_hub.auth.dto.TotpSetupResponse;
import com.course_aura_hub.auth.model.User;

import java.util.UUID;

/**
 * TOTP two-factor authentication: enrollment, login challenges and recovery codes.
 */
public interface MfaService {

    MfaStatusResponse status(UUID userId);

    /** Starts (or restarts) enrollment; 2FA is not enforced until {@link #confirmTotpSetup} succeeds. */
    TotpSetupResponse beginTotpSetup(UUID userId);

    /** Enables 2FA after proving possession of the authenticator and returns new recovery codes. */
    RecoveryCodesResponse confirmTotpSetup(UUID userId, String code);

    void disable(UUID userId, String code, String password);

    RecoveryCodesResponse regenerateRecoveryCodes(UUID userId, String code);

    /** Called after the password (or OAuth2 identity) was accepted. Caller holds the user row lock. */
    MfaChallengeResponse createLoginChallenge(User user, boolean rememberMe);

    /** Completes a login challenge with a TOTP code or a recovery code and starts the session. */
    AuthResponse completeLoginChallenge(String challengeToken, String code, String recoveryCode);
}
