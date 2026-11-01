package com.course_aura_hub.membercard.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.membercard.dto.MemberCardAdjustmentRequest;
import com.course_aura_hub.membercard.dto.MemberCardApplicationPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardApplicationRequest;
import com.course_aura_hub.membercard.dto.MemberCardApplicationResponse;
import com.course_aura_hub.membercard.dto.MemberCardOverviewResponse;
import com.course_aura_hub.membercard.dto.MemberCardPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardResponse;
import com.course_aura_hub.membercard.dto.MemberCardReviewRequest;
import com.course_aura_hub.membercard.dto.MemberCardStatusRequest;
import com.course_aura_hub.membercard.dto.MemberCardTransactionPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardTransactionResponse;
import com.course_aura_hub.membercard.service.MemberCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Member cards", description = "Member card applications, balances and history")
public class MemberCardController {

    private final MemberCardService memberCardService;

    @GetMapping("/api/v1/me/member-card")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My member card", description = "My card, if I have one, and my latest application.")
    public ResponseEntity<ApiResponse<MemberCardOverviewResponse>> mine(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(memberCardService.findMine(authentication.getName())));
    }

    @PostMapping("/api/v1/me/member-card/application")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Apply for a member card")
    public ResponseEntity<ApiResponse<MemberCardApplicationResponse>> apply(
            @Valid @RequestBody MemberCardApplicationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memberCardService.apply(authentication.getName(), request)));
    }

    @GetMapping("/api/v1/me/member-card/transactions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My card history", description = "Newest first.")
    public ResponseEntity<ApiResponse<MemberCardTransactionPageResponse>> myTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                memberCardService.findMyTransactions(authentication.getName(), page, size)));
    }

    @GetMapping("/api/v1/admin/member-cards/applications")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List applications", description = "Admin-only. Newest first, optionally by status.")
    public ResponseEntity<ApiResponse<MemberCardApplicationPageResponse>> applications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(memberCardService.findApplications(status, page, size)));
    }

    @PostMapping("/api/v1/admin/member-cards/applications/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve an application", description = "Admin-only. Issues a card with a zero balance.")
    public ResponseEntity<ApiResponse<MemberCardApplicationResponse>> approve(
            @PathVariable Integer id,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success(memberCardService.approve(id, admin.getUserId())));
    }

    @PostMapping("/api/v1/admin/member-cards/applications/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject an application", description = "Admin-only. The reason is shown to the learner.")
    public ResponseEntity<ApiResponse<MemberCardApplicationResponse>> reject(
            @PathVariable Integer id,
            @Valid @RequestBody MemberCardReviewRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success(memberCardService.reject(id, admin.getUserId(), request.note())));
    }

    @GetMapping("/api/v1/admin/member-cards")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List cards", description = "Admin-only. Newest first.")
    public ResponseEntity<ApiResponse<MemberCardPageResponse>> cards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(memberCardService.findCards(page, size)));
    }

    @GetMapping("/api/v1/admin/member-cards/{id}/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Card history", description = "Admin-only. Newest first.")
    public ResponseEntity<ApiResponse<MemberCardTransactionPageResponse>> transactions(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(memberCardService.findTransactions(id, page, size)));
    }

    @PatchMapping("/api/v1/admin/member-cards/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Suspend or reactivate a card", description = "Admin-only. A suspended card can't pay.")
    public ResponseEntity<ApiResponse<MemberCardResponse>> setStatus(
            @PathVariable Integer id,
            @Valid @RequestBody MemberCardStatusRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                memberCardService.setStatus(id, admin.getUserId(), request.status())));
    }

    @PostMapping("/api/v1/admin/member-cards/{id}/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Top up or deduct",
            description = "Admin-only. Repeating a request with the same idempotency key changes the balance once."
    )
    public ResponseEntity<ApiResponse<MemberCardTransactionResponse>> adjust(
            @PathVariable Integer id,
            @Valid @RequestBody MemberCardAdjustmentRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memberCardService.adjust(id, admin.getUserId(), request)));
    }
}
