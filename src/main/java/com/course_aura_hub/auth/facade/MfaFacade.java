package com.course_aura_hub.auth.facade;

import com.course_aura_hub.auth.dto.MfaStatusResponse;
import com.course_aura_hub.auth.dto.RecoveryCodesResponse;
import com.course_aura_hub.auth.dto.TotpSetupResponse;
import com.course_aura_hub.auth.security.AuthUserPrincipal;

/**
 * Two-factor enrollment entry point; sends security notifications after changes commit.
 */
public interface MfaFacade {

    MfaStatusResponse status(AuthUserPrincipal principal);

    TotpSetupResponse beginTotpSetup(AuthUserPrincipal principal);

    RecoveryCodesResponse confirmTotpSetup(AuthUserPrincipal principal, String code);

    void disable(AuthUserPrincipal principal, String code, String password);

    RecoveryCodesResponse regenerateRecoveryCodes(AuthUserPrincipal principal, String code);
}
