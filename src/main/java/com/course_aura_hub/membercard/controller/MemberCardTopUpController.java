package com.course_aura_hub.membercard.controller;

import com.course_aura_hub.auth.security.AuthUserPrincipal;
import com.course_aura_hub.common.dto.ApiResponse;
import com.course_aura_hub.membercard.dto.MemberCardReportResponse;
import com.course_aura_hub.membercard.dto.MemberCardReviewRequest;
import com.course_aura_hub.membercard.dto.MemberCardTopUpCreateRequest;
import com.course_aura_hub.membercard.dto.MemberCardTopUpPageResponse;
import com.course_aura_hub.membercard.dto.MemberCardTopUpResponse;
import com.course_aura_hub.membercard.service.MemberCardTopUpService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Member card top-ups", description = "Top-up requests and balance reports")
public class MemberCardTopUpController {

    private final MemberCardTopUpService topUpService;

    @PostMapping("/api/v1/me/member-card/top-ups")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Request a top-up", description = "One request can wait at a time. No money moves until approval.")
    public ResponseEntity<ApiResponse<MemberCardTopUpResponse>> request(
            @Valid @RequestBody MemberCardTopUpCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(topUpService.request(authentication.getName(), request)));
    }

    @GetMapping("/api/v1/me/member-card/top-ups")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My top-up requests", description = "The latest 20.")
    public ResponseEntity<ApiResponse<List<MemberCardTopUpResponse>>> mine(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(topUpService.findMine(authentication.getName())));
    }

    @GetMapping("/api/v1/admin/member-cards/top-ups")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List top-up requests", description = "Admin-only. Newest first, optionally by status.")
    public ResponseEntity<ApiResponse<MemberCardTopUpPageResponse>> all(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(topUpService.findAll(status, page, size)));
    }

    @PostMapping("/api/v1/admin/member-cards/top-ups/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a top-up", description = "Admin-only. Adds the amount to the card once.")
    public ResponseEntity<ApiResponse<MemberCardTopUpResponse>> approve(
            @PathVariable Integer id,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success(topUpService.approve(id, admin.getUserId())));
    }

    @PostMapping("/api/v1/admin/member-cards/top-ups/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a top-up", description = "Admin-only. The reason is shown to the learner.")
    public ResponseEntity<ApiResponse<MemberCardTopUpResponse>> reject(
            @PathVariable Integer id,
            @Valid @RequestBody MemberCardReviewRequest request,
            @AuthenticationPrincipal AuthUserPrincipal admin
    ) {
        return ResponseEntity.ok(ApiResponse.success(topUpService.reject(id, admin.getUserId(), request.note())));
    }

    @GetMapping("/api/v1/admin/member-cards/report")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Member card report", description = "Admin-only. Current balances, plus money moved in the last N days.")
    public ResponseEntity<ApiResponse<MemberCardReportResponse>> report(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.success(topUpService.report(days)));
    }
}
