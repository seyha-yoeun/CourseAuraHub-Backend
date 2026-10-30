package com.course_aura_hub.auth.controller;

import com.course_aura_hub.auth.dto.MfaCodeRequest;
import com.course_aura_hub.auth.dto.MfaDisableRequest;
import com.course_aura_hub.auth.dto.MfaStatusResponse;
import com.course_aura_hub.auth.dto.RecoveryCodesResponse;
import com.course_aura_hub.auth.dto.TotpSetupResponse;
import com.course_aura_hub.auth.facade.MfaFacade;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
@Tag(name = "Two-factor authentication", description = "TOTP enrollment and recovery codes for the signed-in user")
public class MfaController {

    private final MfaFacade mfaFacade;

    @GetMapping("/status")
    @Operation(summary = "Two-factor status")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> status(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(mfaFacade.status(principal)));
    }

    @PostMapping("/totp/setup")
    @Operation(summary = "Start authenticator enrollment", description = "Returns a secret and otpauth URI. 2FA is enforced only after /totp/confirm.")
    public ResponseEntity<ApiResponse<TotpSetupResponse>> setup(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Scan the QR code, then confirm with a code", mfaFacade.beginTotpSetup(principal)));
    }

    @PostMapping("/totp/confirm")
    @Operation(summary = "Confirm enrollment", description = "Enables 2FA and returns single-use recovery codes (shown only once).")
    public ResponseEntity<ApiResponse<RecoveryCodesResponse>> confirm(
            @Valid @RequestBody MfaCodeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success("Two-factor authentication enabled",
                mfaFacade.confirmTotpSetup(principal, request.code())));
    }

    @PostMapping("/recovery-codes")
    @Operation(summary = "Regenerate recovery codes", description = "Requires a current authenticator code; previous codes stop working.")
    public ResponseEntity<ApiResponse<RecoveryCodesResponse>> regenerate(
            @Valid @RequestBody MfaCodeRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success("Recovery codes regenerated",
                mfaFacade.regenerateRecoveryCodes(principal, request.code())));
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable two-factor authentication", description = "Requires an authenticator or recovery code, plus the password for accounts that have one.")
    public ResponseEntity<ApiResponse<Void>> disable(
            @Valid @RequestBody MfaDisableRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal
    ) {
        mfaFacade.disable(principal, request.code(), request.password());
        return ResponseEntity.ok(ApiResponse.success("Two-factor authentication disabled", null));
    }
}
