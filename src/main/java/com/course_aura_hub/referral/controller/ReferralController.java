package com.course_aura_hub.referral.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.referral.dto.ReferralDtos;
import com.course_aura_hub.referral.service.ReferralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Referrals", description = "Refer friends and earn member card credit")
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/api/v1/me/referral")
    @Operation(summary = "My referral code", description = "My code and link, and the friends who used it "
            + "(first names only). The reward reaches my member card once a friend's first purchase is past the hold.")
    public ResponseEntity<ApiResponse<ReferralDtos.MyReferral>> mine(@AuthenticationPrincipal AuthUserPrincipal user) {
        return ResponseEntity.ok(ApiResponse.success(referralService.mine(user.getUserId())));
    }

    @PostMapping("/api/v1/me/referral/claim")
    @Operation(summary = "Use a referral code", description = "Once, within the first days after signing up and "
            + "before the first purchase. Works for any sign-in method, so call it after sign-up with the ?ref= code.")
    public ResponseEntity<ApiResponse<ReferralDtos.ClaimResponse>> claim(
            @Valid @RequestBody ReferralDtos.ClaimRequest request,
            @AuthenticationPrincipal AuthUserPrincipal user
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Referral code applied",
                referralService.claim(user.getUserId(), request.code())));
    }

    @GetMapping("/api/v1/admin/referrals")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Referrals", description = "Admin-only. status signed_up, qualified, rewarded or all.")
    public ResponseEntity<ApiResponse<ReferralDtos.AdminPage>> all(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(referralService.findForAdmin(status, page, size)));
    }
}
