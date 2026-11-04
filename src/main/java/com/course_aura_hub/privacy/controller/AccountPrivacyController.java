package com.course_aura_hub.privacy.controller;

import com.course_aura_hub.auth.security.AuthCookieService;
import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.privacy.dto.AccountDeletionRequest;
import com.course_aura_hub.privacy.dto.DataExportResponse;
import com.course_aura_hub.privacy.service.AccountPrivacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Privacy", description = "Download my data and delete my account")
public class AccountPrivacyController {

    private final AccountPrivacyService privacyService;
    private final AuthCookieService authCookieService;

    @GetMapping("/data-export")
    @Operation(summary = "Download my data", description = "Everything stored about the caller, as JSON.")
    public ResponseEntity<ApiResponse<DataExportResponse>> export(@AuthenticationPrincipal AuthUserPrincipal principal) {
        return ResponseEntity.ok()
                .header("Cache-Control", "private, no-store")
                .body(ApiResponse.success(privacyService.export(principal.getUserId())));
    }

    @PostMapping("/account/deletion")
    @Operation(
            summary = "Delete my account",
            description = "Needs the account email as confirmation, and the password for accounts that have one. "
                    + "Removes personal data, signs out every device and clears the auth cookies. "
                    + "Orders, member card history and certificates stay, under an anonymous name."
    )
    public ResponseEntity<ApiResponse<Void>> delete(
            @Valid @RequestBody AccountDeletionRequest request,
            @AuthenticationPrincipal AuthUserPrincipal principal,
            HttpServletResponse response
    ) {
        privacyService.deleteAccount(principal.getUserId(), request.password(), request.confirmation());
        authCookieService.clearAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success("Your account has been deleted", null));
    }
}
