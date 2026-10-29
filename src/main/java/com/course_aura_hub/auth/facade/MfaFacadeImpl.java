package com.course_aura_hub.auth.facade;

import com.course_aura_hub.auth.dto.MfaStatusResponse;
import com.course_aura_hub.auth.dto.RecoveryCodesResponse;
import com.course_aura_hub.auth.dto.TotpSetupResponse;
import com.course_aura_hub.auth.email.AuthEmail;
import com.course_aura_hub.auth.email.AuthEmailService;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.auth.service.AuthService;
import com.course_aura_hub.auth.service.MfaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MfaFacadeImpl implements MfaFacade {

    private final MfaService mfaService;
    private final AuthService authService;
    private final AuthEmailService authEmailService;

    @Override
    public MfaStatusResponse status(AuthUserPrincipal principal) {
        return mfaService.status(principal.getUserId());
    }

    @Override
    public TotpSetupResponse beginTotpSetup(AuthUserPrincipal principal) {
        return mfaService.beginTotpSetup(principal.getUserId());
    }

    @Override
    public RecoveryCodesResponse confirmTotpSetup(AuthUserPrincipal principal, String code) {
        RecoveryCodesResponse codes = mfaService.confirmTotpSetup(principal.getUserId(), code);
        authEmailService.sendQuietly(new AuthEmail.MfaEnabled(principal.getUsername(), firstName(principal)));
        return codes;
    }

    @Override
    public void disable(AuthUserPrincipal principal, String code, String password) {
        mfaService.disable(principal.getUserId(), code, password);
        authEmailService.sendQuietly(new AuthEmail.MfaDisabled(principal.getUsername(), firstName(principal)));
    }

    @Override
    public RecoveryCodesResponse regenerateRecoveryCodes(AuthUserPrincipal principal, String code) {
        return mfaService.regenerateRecoveryCodes(principal.getUserId(), code);
    }

    private String firstName(AuthUserPrincipal principal) {
        return authService.getCurrentUser(principal.getUsername()).firstName();
    }
}
